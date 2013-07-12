/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.engine.view.worker;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;

import com.codahale.metrics.Timer;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.core.config.ConfigSource;
import com.opengamma.core.position.Portfolio;
import com.opengamma.core.position.PortfolioNode;
import com.opengamma.core.position.Position;
import com.opengamma.core.position.Trade;
import com.opengamma.core.position.impl.PortfolioNodeEquivalenceMapper;
import com.opengamma.engine.ComputationTarget;
import com.opengamma.engine.ComputationTargetResolver;
import com.opengamma.engine.ComputationTargetSpecification;
import com.opengamma.engine.MemoryUtils;
import com.opengamma.engine.depgraph.DependencyGraph;
import com.opengamma.engine.depgraph.DependencyGraphExplorer;
import com.opengamma.engine.depgraph.DependencyNode;
import com.opengamma.engine.depgraph.DependencyNodeFilter;
import com.opengamma.engine.function.FunctionParameters;
import com.opengamma.engine.marketdata.MarketDataListener;
import com.opengamma.engine.marketdata.MarketDataSnapshot;
import com.opengamma.engine.marketdata.availability.MarketDataAvailabilityProvider;
import com.opengamma.engine.marketdata.manipulator.DistinctMarketDataSelector;
import com.opengamma.engine.marketdata.manipulator.MarketDataSelectionGraphManipulator;
import com.opengamma.engine.marketdata.manipulator.MarketDataSelector;
import com.opengamma.engine.marketdata.manipulator.NoOpMarketDataSelector;
import com.opengamma.engine.marketdata.manipulator.ScenarioDefinition;
import com.opengamma.engine.marketdata.spec.MarketDataSpecification;
import com.opengamma.engine.resource.EngineResourceReference;
import com.opengamma.engine.target.ComputationTargetReference;
import com.opengamma.engine.target.ComputationTargetType;
import com.opengamma.engine.value.ValueRequirement;
import com.opengamma.engine.value.ValueSpecification;
import com.opengamma.engine.view.ViewCalculationConfiguration;
import com.opengamma.engine.view.ViewComputationResultModel;
import com.opengamma.engine.view.ViewDefinition;
import com.opengamma.engine.view.compilation.CompiledViewDefinitionWithGraphs;
import com.opengamma.engine.view.compilation.CompiledViewDefinitionWithGraphsImpl;
import com.opengamma.engine.view.compilation.ViewCompilationServices;
import com.opengamma.engine.view.compilation.ViewDefinitionCompiler;
import com.opengamma.engine.view.cycle.DefaultViewCycleMetadata;
import com.opengamma.engine.view.cycle.SingleComputationCycle;
import com.opengamma.engine.view.cycle.ViewCycle;
import com.opengamma.engine.view.cycle.ViewCycleMetadata;
import com.opengamma.engine.view.cycle.ViewCycleState;
import com.opengamma.engine.view.execution.ViewCycleExecutionOptions;
import com.opengamma.engine.view.execution.ViewExecutionFlags;
import com.opengamma.engine.view.execution.ViewExecutionOptions;
import com.opengamma.engine.view.impl.ViewProcessContext;
import com.opengamma.engine.view.listener.ComputationResultListener;
import com.opengamma.engine.view.worker.cache.PLAT3249;
import com.opengamma.engine.view.worker.cache.ViewExecutionCacheKey;
import com.opengamma.engine.view.worker.trigger.CombinedViewCycleTrigger;
import com.opengamma.engine.view.worker.trigger.FixedTimeTrigger;
import com.opengamma.engine.view.worker.trigger.RecomputationPeriodTrigger;
import com.opengamma.engine.view.worker.trigger.RunAsFastAsPossibleTrigger;
import com.opengamma.engine.view.worker.trigger.SuccessiveDeltaLimitTrigger;
import com.opengamma.engine.view.worker.trigger.ViewCycleEligibility;
import com.opengamma.engine.view.worker.trigger.ViewCycleTrigger;
import com.opengamma.engine.view.worker.trigger.ViewCycleTriggerResult;
import com.opengamma.engine.view.worker.trigger.ViewCycleType;
import com.opengamma.id.ObjectId;
import com.opengamma.id.UniqueId;
import com.opengamma.id.VersionCorrection;
import com.opengamma.id.VersionCorrectionUtils;
import com.opengamma.util.ArgumentChecker;
import com.opengamma.util.NamedThreadPoolFactory;
import com.opengamma.util.PoolExecutor;
import com.opengamma.util.TerminatableJob;
import com.opengamma.util.metric.OpenGammaMetricRegistry;
import com.opengamma.util.monitor.OperationTimer;
import com.opengamma.util.tuple.Pair;

/**
 * The job which schedules and executes computation cycles for a view process. See {@link SingleThreadViewProcessWorkerFactory} for a more detailed description.
 */
public class SingleThreadViewProcessWorker implements MarketDataListener, ViewProcessWorker {

  private static final Logger s_logger = LoggerFactory.getLogger(SingleThreadViewProcessWorker.class);

  private static final ExecutorService s_executor = Executors.newCachedThreadPool(new NamedThreadPoolFactory("Worker"));
  /**
   * Wrapper that allows a thread to be "borrowed" from an executor service.
   */
  /* package*/static final class BorrowedThread implements Runnable {

    private final String _name;

    private final Runnable _job;
    private final CountDownLatch _join = new CountDownLatch(1);
    private Thread _thread;
    private String _originalName;
    public BorrowedThread(final String name, final Runnable job) {
      _name = name;
      _job = job;
    }

    public synchronized Thread.State getState() {
      if (_thread != null) {
        return _thread.getState();
      } else {
        return (_originalName != null) ? Thread.State.TERMINATED : Thread.State.NEW;
      }
    }

    public void join() throws InterruptedException {
      _join.await();
    }

    public void join(long timeout) throws InterruptedException {
      _join.await(timeout, TimeUnit.MILLISECONDS);
    }

    public synchronized void interrupt() {
      if (_thread != null) {
        _thread.interrupt();
      }
    }

    public synchronized boolean isAlive() {
      return _thread != null;
    }

    // Runnable

    @Override
    public void run() {
      synchronized (this) {
        _thread = Thread.currentThread();
        _originalName = _thread.getName();
      }
      try {
        _thread.setName(_originalName + "-" + _name);
        _job.run();
      } finally {
        _thread.setName(_originalName);
        synchronized (this) {
          _thread = null;
        }
        _join.countDown();
      }
    }

  }

  private static final long NANOS_PER_MILLISECOND = 1000000;

  private final ViewProcessWorkerContext _context;

  private final ViewExecutionOptions _executionOptions;
  private final CombinedViewCycleTrigger _masterCycleTrigger = new CombinedViewCycleTrigger();
  private final FixedTimeTrigger _compilationExpiryCycleTrigger;
  private final boolean _executeCycles;
  private final boolean _executeGraphs;
  private final boolean _ignoreCompilationValidity;
  private final boolean _suppressExecutionOnNoMarketData;
  /**
   * The changes to the master trigger that must be made during the next cycle.
   * <p>
   * This has been added as an immediate fix for [PLAT-3291] but could be extended to represent an arbitrary change to add/remove triggers if we wish to support the execution options changing for a
   * running worker.
   */
  private ViewCycleTrigger _masterCycleTriggerChanges;

  private int _cycleCount;

  private EngineResourceReference<SingleComputationCycle> _previousCycleReference;
  /**
   * The current view definition the worker must calculate on.
   */
  private ViewDefinition _viewDefinition;

  /**
   * The most recently compiled form of the view definition. This may have been compiled by this worker, or retrieved from the cache and is being reused.
   */
  private CompiledViewDefinitionWithGraphs _latestCompiledViewDefinition;

  /**
   * The key to use for storing the compiled view definition, or querying it, from the cache shared with other workers. Whenever the market data provider or view definition changes, this must be
   * updated.
   */
  private ViewExecutionCacheKey _executionCacheKey;

  private final Set<ValueSpecification> _marketDataSubscriptions = new HashSet<>();

  private final Set<ValueSpecification> _pendingSubscriptions = Collections.newSetFromMap(new ConcurrentHashMap<ValueSpecification, Boolean>());
  private TargetResolverChangeListener _targetResolverChanges;

  private volatile boolean _wakeOnCycleRequest;

  private volatile boolean _cycleRequested;
  private volatile boolean _forceTriggerCycle;
  /**
   * An updated view definition pushed in by the execution coordinator. When the next cycle runs, this should be used instead of the previous one.
   */
  private final AtomicReference<ViewDefinition> _newViewDefinition = new AtomicReference<>();

  private volatile Future<CompiledViewDefinitionWithGraphsImpl> _compilationTask;

  /**
   * Total time the job has spent "working". This does not include time spent waiting for a trigger. It is a real time spent on all I/O involved in a cycle (e.g. database accesses), graph compilation,
   * market data subscription, graph execution, result dispatch, etc.
   */
  private double _totalTimeNanos;

  /**
   * The market data provider(s) for the current cycles.
   */
  private SnapshottingViewExecutionDataProvider _marketDataProvider;

  /**
   * Flag indicating the market data provider has changed and any nodes sourcing market data into the dependency graph may now be invalid.
   */
  private boolean _marketDataProviderDirty;

  /**
   * The terminatable job wrapper.
   */
  private final TerminatableJob _job;

  /**
   * The thread running this job.
   */
  private final BorrowedThread _thread;

  /**
   * The manipulator for structured market data.
   */
  private final MarketDataSelectionGraphManipulator _marketDataSelectionGraphManipulator;

  /**
   * The market data selectors and function parameters which have been passed in via
   * the ViewDefinition, which are applicable to a specific dependency graph. There
   * will be an entry for each graph in the view, even if the only contents are
   * an empty map.
   */
  private final Map<String, Map<DistinctMarketDataSelector, FunctionParameters>> _specificMarketDataSelectors;

  /**
    Timer to track delta cycle execution time.
   */
  private Timer _deltaCycleTimer;
  /**
   * Timer to track full cycle execution time.
   */
  private Timer _fullCycleTimer;

  /** Forces a rebuild on the next cycle. */
  private volatile boolean _forceGraphRebuild;

  public SingleThreadViewProcessWorker(final ViewProcessWorkerContext context, final ViewExecutionOptions executionOptions, final ViewDefinition viewDefinition) {
    ArgumentChecker.notNull(context, "context");
    ArgumentChecker.notNull(executionOptions, "executionOptions");
    ArgumentChecker.notNull(viewDefinition, "viewDefinition");
    _context = context;
    _executionOptions = executionOptions;
    _cycleRequested = !executionOptions.getFlags().contains(ViewExecutionFlags.WAIT_FOR_INITIAL_TRIGGER);
    _compilationExpiryCycleTrigger = new FixedTimeTrigger();
    addMasterCycleTrigger(_compilationExpiryCycleTrigger);
    if (executionOptions.getFlags().contains(ViewExecutionFlags.TRIGGER_CYCLE_ON_TIME_ELAPSED)) {
      addMasterCycleTrigger(new RecomputationPeriodTrigger(new Supplier<ViewDefinition>() {
        @Override
        public ViewDefinition get() {
          return getViewDefinition();
        }
      }));
    }
    if (executionOptions.getMaxSuccessiveDeltaCycles() != null) {
      addMasterCycleTrigger(new SuccessiveDeltaLimitTrigger(executionOptions.getMaxSuccessiveDeltaCycles()));
    }
    if (executionOptions.getFlags().contains(ViewExecutionFlags.RUN_AS_FAST_AS_POSSIBLE)) {
      if (_cycleRequested) {
        addMasterCycleTrigger(new RunAsFastAsPossibleTrigger());
      } else {
        // Defer the trigger until an initial one has happened
        _masterCycleTriggerChanges = new RunAsFastAsPossibleTrigger();
      }
    }
    _executeCycles = !executionOptions.getFlags().contains(ViewExecutionFlags.COMPILE_ONLY);
    _executeGraphs = !executionOptions.getFlags().contains(ViewExecutionFlags.FETCH_MARKET_DATA_ONLY);
    _suppressExecutionOnNoMarketData = executionOptions.getFlags().contains(ViewExecutionFlags.SKIP_CYCLE_ON_NO_MARKET_DATA);
    _ignoreCompilationValidity = executionOptions.getFlags().contains(ViewExecutionFlags.IGNORE_COMPILATION_VALIDITY);
    _viewDefinition = viewDefinition;

    _specificMarketDataSelectors = extractSpecificSelectors(viewDefinition);

    _marketDataSelectionGraphManipulator = createMarketDataManipulator(
        _executionOptions.getDefaultExecutionOptions(),
        _specificMarketDataSelectors);

    _job = new Job();
    _thread = new BorrowedThread(context.toString(), _job);
    _deltaCycleTimer = OpenGammaMetricRegistry.getSummaryInstance().timer("SingleThreadViewProcessWorker.cycle.delta");
    _fullCycleTimer = OpenGammaMetricRegistry.getSummaryInstance().timer("SingleThreadViewProcessWorker.cycle.full");
    s_executor.submit(_thread);
  }

  /**
   * We can pickup market data manipulators from either the default execution context or from the
   * view definition. Those from the execution context will have their function parameters
   * specified within the execution options as well (either per cycle or default). Manipulators
   * from the view def will have function params specified alongside them.
   *
   * @param executionOptions the execution options to get the selectors from
   * @param specificSelectors the graph-specific selectors
   * @return a market data manipulator combined those found in the execution context and the
   * view defintion
   */
  private MarketDataSelectionGraphManipulator createMarketDataManipulator(ViewCycleExecutionOptions executionOptions,
                                                                          Map<String, Map<DistinctMarketDataSelector, FunctionParameters>> specificSelectors) {

    MarketDataSelector executionOptionsMarketDataSelector = executionOptions != null ?
        executionOptions.getMarketDataSelector() :
        NoOpMarketDataSelector.getInstance();

    return new MarketDataSelectionGraphManipulator(executionOptionsMarketDataSelector, specificSelectors);
  }

  private Map<String, Map<DistinctMarketDataSelector,FunctionParameters>> extractSpecificSelectors(ViewDefinition viewDefinition) {

    ConfigSource configSource = getProcessContext().getConfigSource();
    Collection<ViewCalculationConfiguration> calculationConfigurations = viewDefinition.getAllCalculationConfigurations();

    Map<String, Map<DistinctMarketDataSelector,FunctionParameters>> specificSelectors = new HashMap<>();

    for (ViewCalculationConfiguration calcConfig : calculationConfigurations) {

      UniqueId scenarioId = calcConfig.getScenarioId();
      if (scenarioId != null) {
        ScenarioDefinition scenarioDefinition = configSource.getConfig(ScenarioDefinition.class, scenarioId);
        specificSelectors.put(calcConfig.getName(), new HashMap<>(scenarioDefinition.getDefinitionMap()));
      } else {
        // Ensure we have an entry for each graph, even if selectors are empty
        specificSelectors.put(calcConfig.getName(), ImmutableMap.<DistinctMarketDataSelector, FunctionParameters>of());
      }
    }
    return specificSelectors;
  }

  private ViewProcessWorkerContext getWorkerContext() {
    return _context;
  }

  private ViewExecutionOptions getExecutionOptions() {
    return _executionOptions;
  }

  private ViewProcessContext getProcessContext() {
    return getWorkerContext().getProcessContext();
  }

  private ViewCycleTrigger getMasterCycleTrigger() {
    return _masterCycleTrigger;
  }

  private void addMasterCycleTrigger(final ViewCycleTrigger trigger) {
    _masterCycleTrigger.addTrigger(trigger);
  }

  public FixedTimeTrigger getCompilationExpiryCycleTrigger() {
    return _compilationExpiryCycleTrigger;
  }

  protected BorrowedThread getThread() {
    return _thread;
  }

  protected TerminatableJob getJob() {
    return _job;
  }

  private final class Job extends TerminatableJob {

    /**
     * Determines whether to run, and runs if required, a single computation cycle using the following rules:
     * <ul>
     * <li>A computation cycle can only be triggered if the relevant minimum computation period has passed since the start of the previous cycle.
     * <li>A computation cycle will be forced if the relevant maximum computation period has passed since the start of the previous cycle.
     * <li>A full computation is preferred over a delta computation if both are possible.
     * <li>Performing a full computation also updates the times to the next delta computation; i.e. a full computation is considered to be as good as a delta.
     * </ul>
     */
    @Override
    protected void runOneCycle() {
      // Exception handling is important here to ensure that computation jobs do not just die quietly while consumers are
      // potentially blocked, waiting for results.

      ViewCycleType cycleType;
      try {
        cycleType = waitForNextCycle();
      } catch (final InterruptedException e) {
        return;
      }
      ViewCycleExecutionOptions executionOptions = null;
      try {
        if (!getExecutionOptions().getExecutionSequence().isEmpty()) {
          executionOptions = getExecutionOptions().getExecutionSequence().poll(getExecutionOptions().getDefaultExecutionOptions());
          s_logger.debug("Next cycle execution options: {}", executionOptions);
        }
        if (executionOptions == null) {
          s_logger.info("No more view cycle execution options");
          jobCompleted();
          return;
        }
      } catch (final Exception e) {
        s_logger.error("Error obtaining next view cycle execution options from sequence for " + getWorkerContext(), e);
        return;
      }

      if (executionOptions.getMarketDataSpecifications().isEmpty()) {
        s_logger.error("No market data specifications for cycle");
        cycleExecutionFailed(executionOptions, new OpenGammaRuntimeException("No market data specifications for cycle"));
        return;
      }

      MarketDataSnapshot marketDataSnapshot;
      try {
        SnapshottingViewExecutionDataProvider marketDataProvider = getMarketDataProvider();
        if (marketDataProvider == null ||
            !marketDataProvider.getSpecifications().equals(executionOptions.getMarketDataSpecifications())) {
          if (marketDataProvider != null) {
            s_logger.info("Replacing market data provider between cycles");
          }
          replaceMarketDataProvider(executionOptions.getMarketDataSpecifications());
          marketDataProvider = getMarketDataProvider();
          if (marketDataProvider == null) {
            cycleExecutionFailed(executionOptions, new OpenGammaRuntimeException("Market data specifications " + executionOptions.getMarketDataSpecifications() + "invalid"));
            return;
          }
        }
        // Obtain the snapshot in case it is needed, but don't explicitly initialise it until the data is required
        marketDataSnapshot = marketDataProvider.snapshot();
      } catch (final Exception e) {
        s_logger.error("Error with market data provider", e);
        cycleExecutionFailed(executionOptions, new OpenGammaRuntimeException("Error with market data provider", e));
        return;
      }

      Instant compilationValuationTime;
      try {
        if (executionOptions.getValuationTime() != null) {
          compilationValuationTime = executionOptions.getValuationTime();
        } else {
          // Neither the cycle-specific options nor the defaults have overridden the valuation time so use the time
          // associated with the market data snapshot. To avoid initialising the snapshot perhaps before the required
          // inputs are known or even subscribed to, only ask for an indication at the moment.
          compilationValuationTime = marketDataSnapshot.getSnapshotTimeIndication();
          if (compilationValuationTime == null) {
            throw new OpenGammaRuntimeException("Market data snapshot " + marketDataSnapshot + " produced a null indication of snapshot time");
          }
        }
      } catch (final Exception e) {
        s_logger.error("Error obtaining compilation valuation time", e);
        cycleExecutionFailed(executionOptions, new OpenGammaRuntimeException("Error obtaining compilation valuation time", e));
        return;
      }

      final VersionCorrection versionCorrection = getResolverVersionCorrection(executionOptions);
      VersionCorrectionUtils.lock(versionCorrection);
      try {
        final CompiledViewDefinitionWithGraphs compiledViewDefinition;
        try {
          // Don't query the cache so that the process gets a "compiled" message even if a cached compilation is used
          final CompiledViewDefinitionWithGraphs previous = _latestCompiledViewDefinition;
          if (_ignoreCompilationValidity && (previous != null) && CompiledViewDefinitionWithGraphsImpl.isValidFor(previous, compilationValuationTime)) {
            compiledViewDefinition = previous;
          } else {
            compiledViewDefinition = getCompiledViewDefinition(compilationValuationTime, versionCorrection);
            if (compiledViewDefinition == null) {
              s_logger.warn("Job terminated during view compilation");
              return;
            }
            if ((previous == null) || !previous.getCompilationIdentifier().equals(compiledViewDefinition.getCompilationIdentifier())) {
              if (_targetResolverChanges != null) {
                // We'll try to register for changes that will wake us up for a cycle if market data is not ticking
                if (previous != null) {
                  final Set<UniqueId> subscribedIds = new HashSet<>(previous.getResolvedIdentifiers().values());
                  for (UniqueId uid : compiledViewDefinition.getResolvedIdentifiers().values()) {
                    if (!subscribedIds.contains(uid)) {
                      _targetResolverChanges.watch(uid.getObjectId());
                    }
                  }
                } else {
                  for (UniqueId uid : compiledViewDefinition.getResolvedIdentifiers().values()) {
                    _targetResolverChanges.watch(uid.getObjectId());
                  }
                }
              }
              viewDefinitionCompiled(executionOptions, compiledViewDefinition);
            }
          }
        } catch (final Exception e) {
          final String message = MessageFormat.format("Error obtaining compiled view definition {0} for time {1} at version-correction {2}", getViewDefinition().getUniqueId(),
              compilationValuationTime, versionCorrection);
          s_logger.error(message);
          cycleExecutionFailed(executionOptions, new OpenGammaRuntimeException(message, e));
          return;
        }
        // [PLAT-1174] This is necessary to support global injections by ValueRequirement. The use of a process-context level variable will be bad
        // if there are multiple worker threads that initialise snapshots concurrently.
        getProcessContext().getLiveDataOverrideInjector().setComputationTargetResolver(
            getProcessContext().getFunctionCompilationService().getFunctionCompilationContext().getRawComputationTargetResolver().atVersionCorrection(versionCorrection));
        boolean marketDataSubscribed = false;
        try {
          if (getExecutionOptions().getFlags().contains(ViewExecutionFlags.AWAIT_MARKET_DATA)) {
            // REVIEW jonathan/andrew -- 2013-03-28 -- if the user wants to wait for market data, then assume they mean
            // it and wait as long as it takes. There are mechanisms for cancelling the job.
            setMarketDataSubscriptions(compiledViewDefinition.getMarketDataRequirements());
            marketDataSubscribed = true;
            marketDataSnapshot.init(compiledViewDefinition.getMarketDataRequirements(), Long.MAX_VALUE, TimeUnit.MILLISECONDS);
          } else {
            marketDataSubscribed = false;
            marketDataSnapshot.init();
          }
          if (executionOptions.getValuationTime() == null) {
            executionOptions = executionOptions.copy().setValuationTime(marketDataSnapshot.getSnapshotTime()).create();
          }
        } catch (final Exception e) {
          s_logger.error("Error initializing snapshot {}", marketDataSnapshot);
          cycleExecutionFailed(executionOptions, new OpenGammaRuntimeException("Error initializing snapshot" + marketDataSnapshot, e));
        }

        EngineResourceReference<SingleComputationCycle> cycleReference;
        try {
          cycleReference = createCycle(executionOptions, compiledViewDefinition, versionCorrection);
        } catch (final Exception e) {
          s_logger.error("Error creating next view cycle for " + getWorkerContext(), e);
          return;
        }

        if (_executeCycles) {
          try {
            final SingleComputationCycle singleComputationCycle = cycleReference.get();
            final Map<String, Collection<ComputationTargetSpecification>> configToComputationTargets = new HashMap<>();
            final Map<String, Map<ValueSpecification, Set<ValueRequirement>>> configToTerminalOutputs = new HashMap<>();
            for (DependencyGraphExplorer graphExp : compiledViewDefinition.getDependencyGraphExplorers()) {
              final DependencyGraph graph = graphExp.getWholeGraph();
              configToComputationTargets.put(graph.getCalculationConfigurationName(), graph.getAllComputationTargets());
              configToTerminalOutputs.put(graph.getCalculationConfigurationName(), graph.getTerminalOutputs());
            }
            if (isTerminated()) {
              cycleReference.release();
              return;
            }
            cycleStarted(new DefaultViewCycleMetadata(
                cycleReference.get().getUniqueId(),
                marketDataSnapshot.getUniqueId(),
                compiledViewDefinition.getViewDefinition().getUniqueId(),
                versionCorrection,
                executionOptions.getValuationTime(),
                singleComputationCycle.getAllCalculationConfigurationNames(),
                configToComputationTargets,
                configToTerminalOutputs));
            if (isTerminated()) {
              cycleReference.release();
              return;
            }
            if (!marketDataSubscribed) {
              setMarketDataSubscriptions(compiledViewDefinition.getMarketDataRequirements());
            }
            executeViewCycle(cycleType, cycleReference, marketDataSnapshot);
          } catch (final InterruptedException e) {
            // Execution interrupted - don't propagate as failure
            s_logger.info("View cycle execution interrupted for {}", getWorkerContext());
            cycleReference.release();
            return;
          } catch (final Exception e) {
            // Execution failed
            s_logger.error("View cycle execution failed for " + getWorkerContext(), e);
            cycleReference.release();
            cycleExecutionFailed(executionOptions, e);
            return;
          }
        }

        // Don't push the results through if we've been terminated, since another computation job could be running already
        // and the fact that we've been terminated means the view is no longer interested in the result. Just die quietly.
        if (isTerminated()) {
          cycleReference.release();
          return;
        }

        if (_executeCycles) {
          cycleCompleted(cycleReference.get());
        }

        if (getExecutionOptions().getExecutionSequence().isEmpty()) {
          jobCompleted();
        }

        if (_executeCycles) {
          if (_previousCycleReference != null) {
            _previousCycleReference.release();
          }
          _previousCycleReference = cycleReference;
        }
      } finally {
        VersionCorrectionUtils.unlock(versionCorrection);
      }

    }

    @Override
    protected void postRunCycle() {
      if (_previousCycleReference != null) {
        _previousCycleReference.release();
      }
      unsubscribeFromTargetResolverChanges();
      removeMarketDataProvider();
      cacheCompiledViewDefinition(null);
    }

    @Override
    public void terminate() {
      super.terminate();
      final Future<CompiledViewDefinitionWithGraphsImpl> task = _compilationTask;
      if (task != null) {
        task.cancel(true);
      }
    }

  }

  private void cycleCompleted(final ViewCycle cycle) {
    try {
      getWorkerContext().cycleCompleted(cycle);
    } catch (final Exception e) {
      s_logger.error("Error notifying " + getWorkerContext() + " of view cycle completion", e);
    }
  }

  private void cycleStarted(final ViewCycleMetadata cycleMetadata) {
    try {
      getWorkerContext().cycleStarted(cycleMetadata);
    } catch (final Exception e) {
      s_logger.error("Error notifying " + getWorkerContext() + " of view cycle starting", e);
    }
  }

  private void cycleFragmentCompleted(final ViewComputationResultModel result) {
    try {
      getWorkerContext().cycleFragmentCompleted(result, getViewDefinition());
    } catch (final Exception e) {
      s_logger.error("Error notifying " + getWorkerContext() + " of cycle fragment completion", e);
    }
  }

  private void cycleExecutionFailed(final ViewCycleExecutionOptions executionOptions, final Exception exception) {
    try {
      getWorkerContext().cycleExecutionFailed(executionOptions, exception);
    } catch (final Exception vpe) {
      s_logger.error("Error notifying " + getWorkerContext() + " of the cycle execution error", vpe);
    }
  }

  private void viewDefinitionCompiled(final ViewCycleExecutionOptions executionOptions, final CompiledViewDefinitionWithGraphs compiledViewDefinition) {
    try {
      getWorkerContext().viewDefinitionCompiled(getMarketDataProvider(), compiledViewDefinition);
    } catch (final Exception vpe) {
      s_logger.error("Error notifying " + getWorkerContext() + " of view definition compilation");
    }
  }

  private void viewDefinitionCompilationFailed(final Instant compilationTime, final Exception e) {
    try {
      getWorkerContext().viewDefinitionCompilationFailed(compilationTime, e);
    } catch (final Exception vpe) {
      s_logger.error("Error notifying " + getWorkerContext() + " of the view definition compilation failure", vpe);
    }
  }

  private synchronized ViewCycleType waitForNextCycle() throws InterruptedException {
    while (true) {
      final long currentTimeNanos = System.nanoTime();
      final ViewCycleTriggerResult triggerResult = getMasterCycleTrigger().query(currentTimeNanos);

      ViewCycleEligibility cycleEligibility = triggerResult.getCycleEligibility();
      if (_forceTriggerCycle) {
        cycleEligibility = ViewCycleEligibility.FORCE;
        _forceTriggerCycle = false;
      }
      if (cycleEligibility == ViewCycleEligibility.FORCE || (cycleEligibility == ViewCycleEligibility.ELIGIBLE && _cycleRequested)) {
        _cycleRequested = false;
        ViewCycleType cycleType = triggerResult.getCycleType();
        if (_previousCycleReference == null) {
          // Cannot do a delta if we have no previous cycle
          cycleType = ViewCycleType.FULL;
        }
        try {
          getMasterCycleTrigger().cycleTriggered(currentTimeNanos, cycleType);
        } catch (final Exception e) {
          s_logger.error("Error notifying trigger of intention to execute cycle", e);
        }
        s_logger.debug("Eligible for {} cycle", cycleType);
        if (_masterCycleTriggerChanges != null) {
          // TODO: If we wish to support execution option changes mid-execution, we will need to add/remove any relevant triggers here
          // Currently only the run-as-fast-as-possible trigger becomes valid for the second cycle if we've also got wait-for-initial-trigger
          addMasterCycleTrigger(_masterCycleTriggerChanges);
          _masterCycleTriggerChanges = null;
        }
        return cycleType;
      }

      // Going to sleep
      final long wakeUpTime = triggerResult.getNextStateChangeNanos();
      if (_cycleRequested) {
        s_logger.debug("Sleeping until eligible to perform the next computation cycle");
        // No amount of market data can make us eligible for a computation cycle any sooner.
        _wakeOnCycleRequest = false;
      } else {
        s_logger.debug("Sleeping until forced to perform the next computation cycle");
        _wakeOnCycleRequest = cycleEligibility == ViewCycleEligibility.ELIGIBLE;
      }

      long sleepTime = wakeUpTime - currentTimeNanos;
      sleepTime = Math.max(0, sleepTime);
      sleepTime /= NANOS_PER_MILLISECOND;
      sleepTime += 1; // Could have been rounded down during division so ensure only woken after state change
      s_logger.debug("Waiting for {} ms", sleepTime);
      try {
        // This could wait until end of time. In this case, only marketDataChanged() or triggerCycle() will wake it up
        wait(sleepTime);
      } catch (final InterruptedException e) {
        // We support interruption as a signal that we have been terminated. If we're interrupted without having been
        // terminated, we'll just return to this method and go back to sleep.
        Thread.interrupted();
        s_logger.info("Interrupted while delaying. Continuing operation.");
        throw e;
      }
    }
  }

  private void executeViewCycle(final ViewCycleType cycleType,
      final EngineResourceReference<SingleComputationCycle> cycleReference,
      final MarketDataSnapshot marketDataSnapshot) throws Exception {
    SingleComputationCycle deltaCycle;
    if (cycleType == ViewCycleType.FULL) {
      s_logger.info("Performing full computation");
      deltaCycle = null;
    } else {
      s_logger.info("Performing delta computation");
      deltaCycle = _previousCycleReference.get();
      if ((deltaCycle != null) && (deltaCycle.getState() != ViewCycleState.EXECUTED)) {
        // Can only do a delta cycle if the previous was valid
        deltaCycle = null;
      }
    }
    boolean continueExecution = cycleReference.get().preExecute(deltaCycle, marketDataSnapshot, _suppressExecutionOnNoMarketData);
    if (_executeGraphs && continueExecution) {
      try {
        cycleReference.get().execute();
      } catch (final InterruptedException e) {
        Thread.interrupted();
        // In reality this means that the job has been terminated, and it will end as soon as we return from this method.
        // In case the thread has been interrupted without terminating the job, we tidy everything up as if the
        // interrupted cycle never happened so that deltas will be calculated from the previous cycle.
        s_logger.info("Interrupted while executing a computation cycle. No results will be output from this cycle.");
        throw e;
      } catch (final Exception e) {
        s_logger.error("Error while executing view cycle", e);
        throw e;
      }
    } else {
      s_logger.debug("Skipping graph execution");
    }
    cycleReference.get().postExecute();
    final long durationNanos = cycleReference.get().getDuration().toNanos();
    final Timer timer = deltaCycle != null ? _deltaCycleTimer : _fullCycleTimer;
    if (timer != null) {
      timer.update(durationNanos, TimeUnit.NANOSECONDS);
    }
    _totalTimeNanos += durationNanos;
    _cycleCount += 1;
    s_logger.info("Last latency was {} ms, Average latency is {} ms",
        durationNanos / NANOS_PER_MILLISECOND,
        (_totalTimeNanos / _cycleCount) / NANOS_PER_MILLISECOND);
  }

  private void jobCompleted() {
    s_logger.info("Computation job completed for {}", getWorkerContext());
    try {
      getWorkerContext().workerCompleted();
    } catch (final Exception e) {
      s_logger.error("Error notifying " + getWorkerContext() + " of computation job completion", e);
    }
    getJob().terminate();
  }

  private EngineResourceReference<SingleComputationCycle> createCycle(final ViewCycleExecutionOptions executionOptions,
      final CompiledViewDefinitionWithGraphs compiledViewDefinition, final VersionCorrection versionCorrection) {

    // [PLAT-3581] Is the check below still necessary? The logic to create the valuation time for compilation is the same as that for
    // populating the valuation time on the execution options that this detects.

    // View definition was compiled based on compilation options, which might have only included an indicative
    // valuation time. A further check ensures that the compiled view definition is still valid.
    if (!CompiledViewDefinitionWithGraphsImpl.isValidFor(compiledViewDefinition, executionOptions.getValuationTime())) {
      throw new OpenGammaRuntimeException("Compiled view definition " + compiledViewDefinition + " not valid for execution options " + executionOptions);
    }

    final UniqueId cycleId = getProcessContext().getCycleIdentifiers().get();
    final ComputationResultListener streamingResultListener = new ComputationResultListener() {
      @Override
      public void resultAvailable(final ViewComputationResultModel result) {
        cycleFragmentCompleted(result);
      }
    };
    final SingleComputationCycle cycle = new SingleComputationCycle(cycleId, streamingResultListener, getProcessContext(), compiledViewDefinition, executionOptions, versionCorrection);
    return getProcessContext().getCycleManager().manage(cycle);
  }

  private void subscribeToTargetResolverChanges() {
    if (_targetResolverChanges == null) {
      _targetResolverChanges = new TargetResolverChangeListener() {
        @Override
        protected void onChanged() {
          requestCycle();
        }
      };
      getProcessContext().getFunctionCompilationService().getFunctionCompilationContext().getRawComputationTargetResolver().changeManager().addChangeListener(_targetResolverChanges);
    }
  }

  private void unsubscribeFromTargetResolverChanges() {
    if (_targetResolverChanges != null) {
      getProcessContext().getFunctionCompilationService().getFunctionCompilationContext().getRawComputationTargetResolver().changeManager().removeChangeListener(_targetResolverChanges);
      _targetResolverChanges = null;
    }
  }

  private static Instant now() {
    // TODO: The distributed caches use a message bus for eventual consistency. This should really be (NOW - maximum permitted clock drift - eventual consistency time limit)
    return Instant.now();
  }

  private VersionCorrection getResolverVersionCorrection(final ViewCycleExecutionOptions viewCycleOptions) {
    VersionCorrection vc = null;
    do {
      vc = viewCycleOptions.getResolverVersionCorrection();
      if (vc != null) {
        break;
      }
      final ViewCycleExecutionOptions options = getExecutionOptions().getDefaultExecutionOptions();
      if (options != null) {
        vc = options.getResolverVersionCorrection();
        if (vc != null) {
          break;
        }
      }
      vc = VersionCorrection.LATEST;
    } while (false);
    // Note: NOW means NOW as the caller has requested LATEST. We should not be using the valuation time.
    if (vc.getCorrectedTo() == null) {
      if (vc.getVersionAsOf() == null) {
        if (!_ignoreCompilationValidity) {
          subscribeToTargetResolverChanges();
        }
        return vc.withLatestFixed(now());
      } else {
        vc = vc.withLatestFixed(now());
      }
    } else if (vc.getVersionAsOf() == null) {
      vc = vc.withLatestFixed(now());
    }
    unsubscribeFromTargetResolverChanges();
    return vc;
  }

  private PortfolioNodeEquivalenceMapper getNodeEquivalenceMapper() {
    return new PortfolioNodeEquivalenceMapper();
  }

  private void markMappedPositions(final PortfolioNode node, final Map<UniqueId, Position> positions) {
    for (Position position : node.getPositions()) {
      positions.put(position.getUniqueId(), null);
    }
    for (PortfolioNode child : node.getChildNodes()) {
      markMappedPositions(child, positions);
    }
  }

  private void findUnmapped(final PortfolioNode node, final Map<UniqueId, UniqueId> mapped, final Set<UniqueId> unmapped, final Map<UniqueId, Position> positions) {
    if (mapped.containsKey(node.getUniqueId())) {
      // This node is mapped; as are the nodes underneath it, so just mark the child positions
      markMappedPositions(node, positions);
    } else {
      // This node is unmapped - mark it as such and check the nodes underneath it
      unmapped.add(node.getUniqueId());
      for (PortfolioNode child : node.getChildNodes()) {
        findUnmapped(child, mapped, unmapped, positions);
      }
      // Any child positions (and their trades) are unmapped if, and only if, they are not referenced by anything else
      for (Position position : node.getPositions()) {
        if (!positions.containsKey(position.getUniqueId())) {
          positions.put(position.getUniqueId(), position);
        }
      }
    }
  }

  private void findUnmapped(final PortfolioNode node, final Map<UniqueId, UniqueId> mapped, final Set<UniqueId> unmapped) {
    final Map<UniqueId, Position> positions = new HashMap<>();
    findUnmapped(node, mapped, unmapped, positions);
    for (Map.Entry<UniqueId, Position> position : positions.entrySet()) {
      if (position.getValue() != null) {
        unmapped.add(position.getKey());
        for (Trade trade : position.getValue().getTrades()) {
          unmapped.add(trade.getUniqueId());
        }
      }
    }
  }

  private Set<UniqueId> rewritePortfolioNodes(final Map<String, Pair<DependencyGraph, Set<ValueRequirement>>> previousGraphs, final CompiledViewDefinitionWithGraphs compiledViewDefinition,
      final Portfolio newPortfolio) {
    // Map any nodes from the old portfolio structure to the new one
    final Map<UniqueId, UniqueId> mapped;
    if (newPortfolio != null) {
      mapped = getNodeEquivalenceMapper().getEquivalentNodes(compiledViewDefinition.getPortfolio().getRootNode(), newPortfolio.getRootNode());
    } else {
      mapped = Collections.emptyMap();
    }
    // Identify anything not (immediately) mapped to the new portfolio structure
    final Set<UniqueId> unmapped = new HashSet<>();
    findUnmapped(compiledViewDefinition.getPortfolio().getRootNode(), mapped, unmapped);
    if (s_logger.isDebugEnabled()) {
      s_logger.debug("Mapping {} portfolio nodes to new structure, unmapping {} targets", mapped.size(), unmapped.size());
    }
    // For anything not mapped, remove the terminal outputs from the graph
    for (final ViewCalculationConfiguration calcConfig : compiledViewDefinition.getViewDefinition().getAllCalculationConfigurations()) {
      final Set<ValueRequirement> specificRequirements = calcConfig.getSpecificRequirements();
      final Pair<DependencyGraph, Set<ValueRequirement>> previousGraphEntry = previousGraphs.get(calcConfig.getName());
      if (previousGraphEntry == null) {
        continue;
      }
      final DependencyGraph previousGraph = previousGraphEntry.getFirst();
      final Map<ValueSpecification, Set<ValueRequirement>> terminalOutputs = previousGraph.getTerminalOutputs();
      final ValueSpecification[] removeSpecifications = new ValueSpecification[terminalOutputs.size()];
      @SuppressWarnings("unchecked")
      final List<ValueRequirement>[] removeRequirements = new List[terminalOutputs.size()];
      int remove = 0;
      for (final Map.Entry<ValueSpecification, Set<ValueRequirement>> entry : terminalOutputs.entrySet()) {
        if (unmapped.contains(entry.getKey().getTargetSpecification().getUniqueId())) {
          List<ValueRequirement> removal = null;
          for (final ValueRequirement requirement : entry.getValue()) {
            if (!specificRequirements.contains(requirement)) {
              if (removal == null) {
                removal = new ArrayList<>(entry.getValue().size());
              }
              removal.add(requirement);
            }
            // Anything that was in the specific requirements will be captured by the standard invalid identifier tests
          }
          if (removal != null) {
            removeSpecifications[remove] = entry.getKey();
            removeRequirements[remove++] = removal;
          }
        }
      }
      for (int i = 0; i < remove; i++) {
        previousGraph.removeTerminalOutputs(removeRequirements[i], removeSpecifications[i]);
      }
      if (!mapped.isEmpty()) {
        final ComputationTargetIdentifierRemapVisitor remapper = new ComputationTargetIdentifierRemapVisitor(mapped);
        final Collection<Object> replacements = new ArrayList<>(mapped.size() * 2);
        for (DependencyNode node : previousGraph.getDependencyNodes()) {
          final ComputationTargetSpecification newTarget = remapper.remap(node.getComputationTarget());
          if (newTarget != null) {
            replacements.add(node);
            replacements.add(newTarget);
          }
        }
        Iterator<Object> itrReplacements = replacements.iterator();
        while (itrReplacements.hasNext()) {
          final DependencyNode node = (DependencyNode) itrReplacements.next();
          final ComputationTargetSpecification newTarget = (ComputationTargetSpecification) itrReplacements.next();
          s_logger.debug("Rewriting {} to {}", node, newTarget);
          previousGraph.replaceNode(node, newTarget);
        }
        // Rewrite the original value requirements that might have referenced the original nodes
        for (Map.Entry<ValueSpecification, Set<ValueRequirement>> terminalOutput : previousGraph.getTerminalOutputs().entrySet()) {
          final Set<ValueRequirement> oldReqs = terminalOutput.getValue();
          replacements.clear();
          for (ValueRequirement req : oldReqs) {
            final ComputationTargetReference newTarget = req.getTargetReference().accept(remapper);
            if (newTarget != null) {
              replacements.add(req);
              replacements.add(MemoryUtils.instance(new ValueRequirement(req.getValueName(), newTarget, req.getConstraints())));
            }
          }
          if (!replacements.isEmpty()) {
            itrReplacements = replacements.iterator();
            while (itrReplacements.hasNext()) {
              final ValueRequirement oldReq = (ValueRequirement) itrReplacements.next();
              final ValueRequirement newReq = (ValueRequirement) itrReplacements.next();
              oldReqs.remove(oldReq);
              oldReqs.add(newReq);
            }
          }
        }
      }
    }
    // Remove any PORTFOLIO nodes and any unmapped PORTFOLIO_NODE nodes with the filter
    filterPreviousGraphs(previousGraphs, new InvalidPortfolioDependencyNodeFilter(unmapped), null);
    return new HashSet<>(mapped.values());
  }

  /**
   * Returns the set of unique identifiers that were previously used as targets in the dependency graph for object identifiers (or external identifiers) that now resolve differently.
   * 
   * @param previousResolutions the previous cycle's resolution of identifiers, not null
   * @param versionCorrection the resolver version correction for this cycle, not null
   * @return the invalid identifier set, or null if none are invalid, this is a map from the old unique identifier to the new resolution
   */
  private Map<UniqueId, ComputationTargetSpecification> getInvalidIdentifiers(final Map<ComputationTargetReference, UniqueId> previousResolutions, final VersionCorrection versionCorrection) {
    long t = -System.nanoTime();
    final Set<ComputationTargetReference> toCheck;
    if (_targetResolverChanges == null) {
      // Change notifications aren't relevant for historical iteration; must recheck all of the resolutions
      toCheck = previousResolutions.keySet();
    } else {
      // Subscribed to LATEST/LATEST so change manager notifications can filter the set to be checked
      toCheck = Sets.newHashSetWithExpectedSize(previousResolutions.size());
      final Set<ObjectId> allObjectIds = Sets.newHashSetWithExpectedSize(previousResolutions.size());
      for (final Map.Entry<ComputationTargetReference, UniqueId> previousResolution : previousResolutions.entrySet()) {
        final ObjectId oid = previousResolution.getValue().getObjectId();
        if (_targetResolverChanges.isChanged(oid)) {
          // A change was seen on this target
          s_logger.debug("Change observed on {}", oid);
          toCheck.add(previousResolution.getKey());
        }
        allObjectIds.add(oid);
      }
      _targetResolverChanges.watchOnly(allObjectIds);
      if (toCheck.isEmpty()) {
        s_logger.debug("No resolutions (from {}) to check", previousResolutions.size());
        return null;
      } else {
        s_logger.debug("Checking {} of {} resolutions for changed objects", toCheck.size(), previousResolutions.size());
      }
    }
    PoolExecutor previousInstance = PoolExecutor.setInstance(getProcessContext().getFunctionCompilationService().getExecutorService());
    final Map<ComputationTargetReference, ComputationTargetSpecification> specifications = getProcessContext().getFunctionCompilationService().getFunctionCompilationContext()
        .getRawComputationTargetResolver().getSpecificationResolver().getTargetSpecifications(toCheck, versionCorrection);
    PoolExecutor.setInstance(previousInstance);
    t += System.nanoTime();
    Map<UniqueId, ComputationTargetSpecification> invalidIdentifiers = null;
    for (final Map.Entry<ComputationTargetReference, UniqueId> target : previousResolutions.entrySet()) {
      final ComputationTargetSpecification resolved = specifications.get(target.getKey());
      if ((resolved != null) && target.getValue().equals(resolved.getUniqueId())) {
        // No change
        s_logger.debug("No change resolving {}", target);
      } else if (toCheck.contains(target.getKey())) {
        // Identifier no longer resolved, or resolved differently
        s_logger.info("New resolution of {} to {}", target, resolved);
        if (invalidIdentifiers == null) {
          invalidIdentifiers = new HashMap<>();
        }
        invalidIdentifiers.put(target.getValue(), resolved);
      }
    }
    s_logger.info("{} resolutions checked in {}ms", toCheck.size(), t / 1e6);
    return invalidIdentifiers;
  }

  private void getInvalidMarketData(final DependencyGraph graph, final InvalidMarketDataDependencyNodeFilter filter) {
    final PoolExecutor.Service<?> slaveJobs = getProcessContext().getFunctionCompilationService().getExecutorService().createService(null);
    // 32 was chosen fairly arbitrarily. Before doing this 502 node checks was taking 700ms. After this it is taking 180ms. 
    final int jobSize = 32;
    InvalidMarketDataDependencyNodeFilter.VisitBatch visit = filter.visit(jobSize);
    for (ValueSpecification marketData : graph.getAllRequiredMarketData()) {
      if (visit.isFull()) {
        slaveJobs.execute(visit);
        visit = filter.visit(jobSize);
      }
      final DependencyNode node = graph.getNodeProducing(marketData);
      visit.add(marketData, node);
    }
    visit.run();
    try {
      slaveJobs.join();
    } catch (InterruptedException e) {
      throw new OpenGammaRuntimeException("Interrupted", e);
    }
  }

  /**
   * Returns the set of value specifications from Market Data sourcing nodes that are not valid for the new data provider.
   * <p>
   * The cost of applying a filter can be quite high and in the historical simulation case seldom excludes nodes. To optimise this case we consider the market data sourcing nodes first to determine
   * whether the filter should be applied.
   * 
   * @param previousGraphs the previous graphs that have already been part processed, null if no preprocessing has occurred
   * @param compiledViewDefinition the cached compilation containing previous graphs if {@code previousGraphs} is null
   * @param filter the filter to pass details of the nodes to
   */
  private void getInvalidMarketData(final Map<String, Pair<DependencyGraph, Set<ValueRequirement>>> previousGraphs,
      final CompiledViewDefinitionWithGraphs compiledViewDefinition, final InvalidMarketDataDependencyNodeFilter filter) {
    if (previousGraphs != null) {
      for (Pair<DependencyGraph, Set<ValueRequirement>> previousGraph : previousGraphs.values()) {
        getInvalidMarketData(previousGraph.getFirst(), filter);
      }
    } else {
      for (DependencyGraphExplorer graphExp : compiledViewDefinition.getDependencyGraphExplorers()) {
        getInvalidMarketData(graphExp.getWholeGraph(), filter);
      }
    }
  }

  /**
   * Mark a set of nodes for inclusion (TRUE) or exclusion (FALSE) based on the filter. A node is included if the filter accepts it and all of its inputs are also marked for inclusion. A node is
   * excluded if the filter rejects it or any of its inputs are rejected. This will operate recursively, processing all nodes to the leaves of the graph.
   * <p>
   * The {@link DependencyGraph#subGraph} operation doesn't work for us as it can leave nodes in the sub-graph that have inputs that aren't in the graph. Invalid nodes identified by the filter need to
   * remove all the graph up to the terminal output root so that we can rebuild it.
   * 
   * @param include the map to build the result into
   * @param nodes the nodes to process
   * @param filter the filter to apply to the nodes
   * @return true if all of the nodes in the collection were included
   */
  private static boolean includeNodes(final Map<DependencyNode, Boolean> include, final Collection<DependencyNode> nodes, final DependencyNodeFilter filter) {
    boolean includedAll = true;
    for (final DependencyNode node : nodes) {
      final Boolean match = include.get(node);
      if (match == null) {
        if (filter.accept(node)) {
          if (includeNodes(include, node.getInputNodes(), filter)) {
            include.put(node, Boolean.TRUE);
          } else {
            includedAll = false;
            include.put(node, Boolean.FALSE);
          }
        } else {
          includedAll = false;
          include.put(node, Boolean.FALSE);
        }
      } else {
        if (match == Boolean.FALSE) {
          includedAll = false;
        }
      }
    }
    return includedAll;
  }

  private Map<String, Pair<DependencyGraph, Set<ValueRequirement>>> getPreviousGraphs(Map<String, Pair<DependencyGraph, Set<ValueRequirement>>> previousGraphs,
      final CompiledViewDefinitionWithGraphs compiledViewDefinition) {
    if (previousGraphs == null) {
      final Collection<DependencyGraphExplorer> graphExps = compiledViewDefinition.getDependencyGraphExplorers();
      previousGraphs = Maps.newHashMapWithExpectedSize(graphExps.size());
      for (DependencyGraphExplorer graphExp : graphExps) {
        final DependencyGraph graph = graphExp.getWholeGraph();
        previousGraphs.put(graph.getCalculationConfigurationName(), Pair.<DependencyGraph, Set<ValueRequirement>>of(graph, new HashSet<ValueRequirement>()));
      }
    }
    return previousGraphs;
  }

  /**
   * Maintain the previously used dependency graphs by applying a node filter that identifies invalid nodes that must be recalculated (implying everything dependent on them must also be rebuilt). The
   * first call will extract the previously compiled graphs, subsequent calls will update the structure invalidating more nodes and increasing the number of missing requirements.
   * 
   * @param previousGraphs the previously used graphs as a map from calculation configuration name to the graph and the value requirements that need to be recalculated, not null
   * @param filter the filter to identify invalid nodes, not null
   * @param unchangedNodes optional identifiers of unchanged portfolio nodes; any nodes filtered out must be removed from this
   */
  private void filterPreviousGraphs(final Map<String, Pair<DependencyGraph, Set<ValueRequirement>>> previousGraphs, final DependencyNodeFilter filter, final Set<UniqueId> unchangedNodes) {
    final Iterator<Map.Entry<String, Pair<DependencyGraph, Set<ValueRequirement>>>> itr = previousGraphs.entrySet().iterator();
    while (itr.hasNext()) {
      final Map.Entry<String, Pair<DependencyGraph, Set<ValueRequirement>>> entry = itr.next();
      final DependencyGraph graph = entry.getValue().getFirst();
      if (graph.getSize() == 0) {
        continue;
      }
      final Collection<DependencyNode> nodes = graph.getDependencyNodes();
      final Map<DependencyNode, Boolean> include = Maps.newHashMapWithExpectedSize(nodes.size());
      includeNodes(include, nodes, filter);
      assert nodes.size() == include.size();
      final Map<ValueSpecification, Set<ValueRequirement>> terminalOutputs = graph.getTerminalOutputs();
      final Set<ValueRequirement> missingRequirements = entry.getValue().getSecond();
      final DependencyGraph filtered = graph.subGraph(new DependencyNodeFilter() {
        @Override
        public boolean accept(final DependencyNode node) {
          if (include.get(node) == Boolean.TRUE) {
            return true;
          } else {
            s_logger.debug("Discarding {} from dependency graph for {}", node, entry.getKey());
            for (final ValueSpecification output : node.getOutputValues()) {
              final Set<ValueRequirement> terminal = terminalOutputs.get(output);
              if (terminal != null) {
                missingRequirements.addAll(terminal);
              }
            }
            if (unchangedNodes != null) {
              unchangedNodes.remove(node.getComputationTarget().getUniqueId());
            }
            return false;
          }
        }
      });
      if (filtered.getSize() == 0) {
        s_logger.info("Discarded total dependency graph for {}", entry.getKey());
        itr.remove();
      } else {
        if (s_logger.isInfoEnabled()) {
          s_logger.info("Removed {} nodes from dependency graph for {} by {}",
              nodes.size() - filtered.getSize(),
              entry.getKey(),
              filter);
        }
        entry.setValue(Pair.of(filtered, missingRequirements));
      }
    }
  }

  private CompiledViewDefinitionWithGraphs getCompiledViewDefinition(final Instant valuationTime, final VersionCorrection versionCorrection) {
    final long functionInitId = getProcessContext().getFunctionCompilationService().getFunctionCompilationContext().getFunctionInitId();
    updateViewDefinitionIfRequired();
    CompiledViewDefinitionWithGraphs compiledViewDefinition = null;
    final Pair<Lock, Lock> executionCacheLocks = getProcessContext().getExecutionCacheLock().get(_executionCacheKey, valuationTime, versionCorrection);
    executionCacheLocks.getSecond().lock();
    executionCacheLocks.getFirst().lock();
    boolean broadLock = true;
    try {
      Map<String, Pair<DependencyGraph, Set<ValueRequirement>>> previousGraphs = null;
      ConcurrentMap<ComputationTargetReference, UniqueId> previousResolutions = null;
      Set<UniqueId> changedPositions = null;
      Set<UniqueId> unchangedNodes = null;
      if (!_forceGraphRebuild) {
        compiledViewDefinition = getCachedCompiledViewDefinition(valuationTime, versionCorrection);
        changedPositions = null;
        unchangedNodes = null;
        boolean marketDataProviderDirty = _marketDataProviderDirty;
        _marketDataProviderDirty = false;
        if (compiledViewDefinition != null) {
          executionCacheLocks.getFirst().unlock();
          broadLock = false;
          do {
            // The cast below is bad, but only temporary -- the function initialiser id needs to go
            if (functionInitId != ((CompiledViewDefinitionWithGraphsImpl) compiledViewDefinition).getFunctionInitId()) {
              // The function repository has been reinitialized which invalidates any previous graphs
              // TODO: [PLAT-2237, PLAT-1623, PLAT-2240] Get rid of this
              break;
            }
            final Map<ComputationTargetReference, UniqueId> resolvedIdentifiers = compiledViewDefinition.getResolvedIdentifiers();
            // TODO: The check below works well for the historical valuation case, but if the resolver v/c is different for two workers in the
            // group for an otherwise identical cache key then including it in the caching detail may become necessary to handle those cases.
            if (!versionCorrection.equals(compiledViewDefinition.getResolverVersionCorrection())) {
              final Map<UniqueId, ComputationTargetSpecification> invalidIdentifiers = getInvalidIdentifiers(resolvedIdentifiers, versionCorrection);
              if (invalidIdentifiers != null) {
                previousGraphs = getPreviousGraphs(previousGraphs, compiledViewDefinition);
                if ((compiledViewDefinition.getPortfolio() != null) && invalidIdentifiers.containsKey(compiledViewDefinition.getPortfolio().getUniqueId())) {
                  // The portfolio resolution is different, invalidate or rewrite PORTFOLIO and PORTFOLIO_NODE nodes in the graph. Note that incremental
                  // compilation under this circumstance can be flawed if the functions have made notable use of the overall portfolio structure such that
                  // a full re-compilation will yield a different dependency graph to just rewriting the previous one.
                  final ComputationTargetResolver resolver = getProcessContext().getFunctionCompilationService().getFunctionCompilationContext().getRawComputationTargetResolver();
                  final ComputationTargetSpecification portfolioSpec = resolver.getSpecificationResolver().getTargetSpecification(
                      new ComputationTargetSpecification(ComputationTargetType.PORTFOLIO, getViewDefinition().getPortfolioId()), versionCorrection);
                  final ComputationTarget newPortfolio = resolver.resolve(portfolioSpec, versionCorrection);
                  unchangedNodes = rewritePortfolioNodes(previousGraphs, compiledViewDefinition, (Portfolio) newPortfolio.getValue());
                }
                // Invalidate any dependency graph nodes on the invalid targets
                filterPreviousGraphs(previousGraphs, new InvalidTargetDependencyNodeFilter(invalidIdentifiers.keySet()), unchangedNodes);
                previousResolutions = new ConcurrentHashMap<>(resolvedIdentifiers.size());
                for (final Map.Entry<ComputationTargetReference, UniqueId> resolvedIdentifier : resolvedIdentifiers.entrySet()) {
                  if (invalidIdentifiers.containsKey(resolvedIdentifier.getValue())) {
                    if ((unchangedNodes == null) && resolvedIdentifier.getKey().getType().isTargetType(ComputationTargetType.POSITION)) {
                      // At least one position has changed, add all portfolio targets
                      ComputationTargetSpecification ctspec = invalidIdentifiers.get(resolvedIdentifier.getValue());
                      if (ctspec != null) {
                        if (changedPositions == null) {
                          changedPositions = new HashSet<>();
                        }
                        changedPositions.add(ctspec.getUniqueId());
                      }
                    }
                  } else {
                    previousResolutions.put(resolvedIdentifier.getKey(), resolvedIdentifier.getValue());
                  }
                }
              } else {
                compiledViewDefinition = compiledViewDefinition.withResolverVersionCorrection(versionCorrection);
                cacheCompiledViewDefinition(compiledViewDefinition);
              }
            }
            if (!CompiledViewDefinitionWithGraphsImpl.isValidFor(compiledViewDefinition, valuationTime)) {
              // Invalidate any dependency graph nodes that use functions that are no longer valid
              previousGraphs = getPreviousGraphs(previousGraphs, compiledViewDefinition);
              filterPreviousGraphs(previousGraphs, new InvalidFunctionDependencyNodeFilter(valuationTime), unchangedNodes);
            }
            if (marketDataProviderDirty) {
              // Invalidate any market data sourcing nodes that are no longer valid
              final InvalidMarketDataDependencyNodeFilter filter = new InvalidMarketDataDependencyNodeFilter(getProcessContext().getFunctionCompilationService().getFunctionCompilationContext()
                  .getRawComputationTargetResolver().atVersionCorrection(versionCorrection), getMarketDataProvider().getAvailabilityProvider());
              getInvalidMarketData(previousGraphs, compiledViewDefinition, filter);
              if (filter.hasInvalidNodes()) {
                previousGraphs = getPreviousGraphs(previousGraphs, compiledViewDefinition);
                filterPreviousGraphs(previousGraphs, filter, unchangedNodes);
              }
            }
            if (previousGraphs == null) {
              // Existing cached model is valid (an optimization for the common case of similar, increasing valuation times)
              return compiledViewDefinition;
            }
            if (previousResolutions == null) {
              previousResolutions = new ConcurrentHashMap<>(resolvedIdentifiers);
            }
          } while (false);
          executionCacheLocks.getFirst().lock();
          broadLock = true;
        }
      }
      final MarketDataAvailabilityProvider availabilityProvider = getMarketDataProvider().getAvailabilityProvider();
      final ViewCompilationServices compilationServices = getProcessContext().asCompilationServices(availabilityProvider);
      if (previousGraphs != null) {
        s_logger.info("Performing incremental graph compilation");
        _compilationTask = ViewDefinitionCompiler.incrementalCompileTask(getViewDefinition(), compilationServices, valuationTime, versionCorrection, previousGraphs,
            previousResolutions, changedPositions, unchangedNodes);
      } else {
        s_logger.info("Performing full graph compilation");
        _compilationTask = ViewDefinitionCompiler.fullCompileTask(getViewDefinition(), compilationServices, valuationTime, versionCorrection);
      }

      try {
        if (!getJob().isTerminated()) {
          compiledViewDefinition = _compilationTask.get();
          ComputationTargetResolver.AtVersionCorrection resolver =
              getProcessContext().getFunctionCompilationService().getFunctionCompilationContext()
                  .getRawComputationTargetResolver().atVersionCorrection(versionCorrection);
          compiledViewDefinition = initialiseMarketDataManipulation(compiledViewDefinition, resolver);
          cacheCompiledViewDefinition(compiledViewDefinition);
        } else {
          return null;
        }
      } finally {
        _compilationTask = null;
      }

    } catch (final Exception e) {
      final String message = MessageFormat.format("Error compiling view definition {0} for time {1}", getViewDefinition().getUniqueId(), valuationTime);
      viewDefinitionCompilationFailed(valuationTime, new OpenGammaRuntimeException(message, e));
      throw new OpenGammaRuntimeException(message, e);
    } finally {
      _forceGraphRebuild = false;
      if (broadLock) {
        executionCacheLocks.getFirst().unlock();
      }
      executionCacheLocks.getSecond().unlock();
    }
    // [PLAT-984]
    // Assume that valuation times are increasing in real-time towards the expiry of the view definition, so that we
    // can predict the time to expiry. If this assumption is wrong then the worst we do is trigger an unnecessary
    // cycle. In the predicted case, we trigger a cycle on expiry so that any new market data subscriptions are made
    // straight away.
    if ((compiledViewDefinition.getValidTo() != null) && getExecutionOptions().getFlags().contains(ViewExecutionFlags.TRIGGER_CYCLE_ON_MARKET_DATA_CHANGED)) {
      final Duration durationToExpiry = getMarketDataProvider().getRealTimeDuration(valuationTime, compiledViewDefinition.getValidTo());
      final long expiryNanos = System.nanoTime() + durationToExpiry.toNanos();
      _compilationExpiryCycleTrigger.set(expiryNanos, ViewCycleTriggerResult.forceFull());
      // REVIEW Andrew 2012-11-02 -- If we are ticking live, then this is almost right (System.nanoTime will be close to valuationTime, depending on how
      // long the compilation took). If we are running through historical data then this is quite a meaningless trigger.
    } else {
      _compilationExpiryCycleTrigger.reset();
    }
    return compiledViewDefinition;
  }

  private CompiledViewDefinitionWithGraphs initialiseMarketDataManipulation(CompiledViewDefinitionWithGraphs compiledViewDefinition,
                                                                            ComputationTargetResolver.AtVersionCorrection resolver) {

    if (_marketDataSelectionGraphManipulator.hasManipulationsDefined()) {

      s_logger.info("Initialising market data manipulation");

      Map<DependencyGraph, Map<DistinctMarketDataSelector, Set<ValueSpecification>>> selectionsByGraph = new HashMap<>();
      Map<DependencyGraph, Map<DistinctMarketDataSelector, FunctionParameters>> functionParamsByGraph = new HashMap<>();

      for (DependencyGraphExplorer graphExplorer : compiledViewDefinition.getDependencyGraphExplorers()) {

        DependencyGraph graph = graphExplorer.getWholeGraph();
        final Map<DistinctMarketDataSelector, Set<ValueSpecification>> selectorMapping =
            _marketDataSelectionGraphManipulator.modifyDependencyGraph(graph, resolver);

        if (!selectorMapping.isEmpty()) {

          selectionsByGraph.put(graph, selectorMapping);
          Map<DistinctMarketDataSelector, FunctionParameters> params =
              _specificMarketDataSelectors.get(graph.getCalculationConfigurationName());

          // _specificMarketDataSelectors has an entry for each graph, so no null check required
          if (!params.isEmpty()) {

            // Filter the function params so that we only have entries for active selectors
            Map<DistinctMarketDataSelector, FunctionParameters> filteredParams = Maps.filterKeys(
                params,
                new Predicate<DistinctMarketDataSelector>() {
                  @Override
                  public boolean apply(DistinctMarketDataSelector selector) {
                    return selectorMapping.containsKey(selector);
                  }
                });
            functionParamsByGraph.put(graph, filteredParams);
          }
        }
      }

      if (!selectionsByGraph.isEmpty()) {

        s_logger.info("Adding in market data manipulation selections: [{}] and preset function parameters: [{}]",
                      selectionsByGraph, functionParamsByGraph);
        return compiledViewDefinition.withMarketDataManipulationSelections(selectionsByGraph, functionParamsByGraph);

      } else {
        s_logger.info("No market data manipulation selectors matched - no manipulation to be done");
      }
    }
    return compiledViewDefinition;
  }

  /**
   * Gets the cached compiled view definition which may be re-used in subsequent computation cycles.
   * <p>
   * External visibility for tests.
   * 
   * @param valuationTime the indicative valuation time, not null
   * @param resolverVersionCorrection the resolver version correction, not null
   * @return the cached compiled view definition, or null if nothing is currently cached
   */
  public CompiledViewDefinitionWithGraphs getCachedCompiledViewDefinition(final Instant valuationTime, final VersionCorrection resolverVersionCorrection) {
    CompiledViewDefinitionWithGraphs cached = _latestCompiledViewDefinition;
    if (cached != null) {
      boolean resolverMatch = resolverVersionCorrection.equals(cached.getResolverVersionCorrection());
      boolean valuationMatch = CompiledViewDefinitionWithGraphsImpl.isValidFor(cached, valuationTime);
      if (!resolverMatch || !valuationMatch) {
        // Query the cache in case there is a newer one
        cached = getProcessContext().getExecutionCache().getCompiledViewDefinitionWithGraphs(_executionCacheKey);
        if (cached != null) {
          // Only update ours if the one from the cache has a better validity
          if (resolverVersionCorrection.equals(cached.getResolverVersionCorrection())) {
            cached = PLAT3249.deepClone(cached);
            _latestCompiledViewDefinition = cached;
          } else {
            if (!resolverMatch && !valuationMatch && CompiledViewDefinitionWithGraphsImpl.isValidFor(cached, valuationTime)) {
              cached = PLAT3249.deepClone(cached);
              _latestCompiledViewDefinition = cached;
            }
          }
        } else {
          // Nothing in the cache; use the one from last time
          cached = _latestCompiledViewDefinition;
        }
      }
    } else {
      // Query the cache
      cached = getProcessContext().getExecutionCache().getCompiledViewDefinitionWithGraphs(_executionCacheKey);
      if (cached != null) {
        cached = PLAT3249.deepClone(cached);
        _latestCompiledViewDefinition = cached;
      }
    }
    return cached;
  }

  /**
   * Replaces the cached compiled view definition.
   * <p>
   * External visibility for tests.
   * 
   * @param latestCompiledViewDefinition the compiled view definition, may be null
   */
  public void cacheCompiledViewDefinition(final CompiledViewDefinitionWithGraphs latestCompiledViewDefinition) {
    if (latestCompiledViewDefinition != null) {
      getProcessContext().getExecutionCache().setCompiledViewDefinitionWithGraphs(_executionCacheKey, latestCompiledViewDefinition);
    }
    _latestCompiledViewDefinition = latestCompiledViewDefinition;
  }

  /**
   * Gets the view definition currently in use by the computation job.
   * 
   * @return the view definition, not null
   */
  public ViewDefinition getViewDefinition() {
    return _viewDefinition;
  }

  private void updateViewDefinitionIfRequired() {
    final ViewDefinition newViewDefinition = _newViewDefinition.getAndSet(null);
    if (newViewDefinition != null) {
      _viewDefinition = newViewDefinition;
      // TODO [PLAT-3215] Might not need to discard the entire compilation at this point
      cacheCompiledViewDefinition(null);
      SnapshottingViewExecutionDataProvider marketDataProvider = getMarketDataProvider();
      _executionCacheKey = ViewExecutionCacheKey.of(newViewDefinition, marketDataProvider.getAvailabilityProvider());
      // A change in view definition might mean a change in market data user which could invalidate the resolutions
      if (marketDataProvider != null) {
        if (!marketDataProvider.getMarketDataUser().equals(newViewDefinition.getMarketDataUser())) {
          replaceMarketDataProvider(marketDataProvider.getSpecifications());
        }
      }
    }
  }

  private void replaceMarketDataProvider(final List<MarketDataSpecification> marketDataSpecs) {
    // [PLAT-3186] Not a huge overhead, but we could check compatability with the new specs and keep the same provider
    removeMarketDataProvider();
    setMarketDataProvider(marketDataSpecs);
  }

  private void removeMarketDataProvider() {
    if (_marketDataProvider == null) {
      return;
    }
    removeMarketDataSubscriptions();
    _marketDataProvider.removeListener(this);
    _marketDataProvider = null;
    _marketDataProviderDirty = true;
    _executionCacheKey = null;
  }

  private void setMarketDataProvider(final List<MarketDataSpecification> marketDataSpecs) {
    try {
      _marketDataProvider = new SnapshottingViewExecutionDataProvider(getViewDefinition().getMarketDataUser(),
          marketDataSpecs, getProcessContext().getMarketDataProviderResolver());
    } catch (final Exception e) {
      s_logger.error("Failed to create data provider", e);
      _marketDataProvider = null;
    }
    if (_marketDataProvider != null) {
      _marketDataProvider.addListener(this);
      _executionCacheKey = ViewExecutionCacheKey.of(getViewDefinition(), _marketDataProvider.getAvailabilityProvider());
    }
    _marketDataProviderDirty = true;
  }

  private SnapshottingViewExecutionDataProvider getMarketDataProvider() {
    return _marketDataProvider;
  }

  private void setMarketDataSubscriptions(final Set<ValueSpecification> requiredSubscriptions) {
    final Set<ValueSpecification> currentSubscriptions = _marketDataSubscriptions;
    final Set<ValueSpecification> unusedMarketData = Sets.difference(currentSubscriptions, requiredSubscriptions);
    if (!unusedMarketData.isEmpty()) {
      s_logger.debug("{} unused market data subscriptions", unusedMarketData.size());
      removeMarketDataSubscriptions(new ArrayList<>(unusedMarketData));
    }
    final Set<ValueSpecification> newMarketData = Sets.difference(requiredSubscriptions, currentSubscriptions);
    if (!newMarketData.isEmpty()) {
      s_logger.debug("{} new market data requirements", newMarketData.size());
      addMarketDataSubscriptions(new HashSet<>(newMarketData));
    }
  }

  //-------------------------------------------------------------------------
  private void addMarketDataSubscriptions(final Set<ValueSpecification> requiredSubscriptions) {
    final OperationTimer timer = new OperationTimer(s_logger, "Adding {} market data subscriptions", requiredSubscriptions.size());
    _pendingSubscriptions.addAll(requiredSubscriptions);
    _marketDataProvider.subscribe(requiredSubscriptions);
    _marketDataSubscriptions.addAll(requiredSubscriptions);
    try {
      synchronized (_pendingSubscriptions) {
        if (!_pendingSubscriptions.isEmpty()) {
          _pendingSubscriptions.wait();
        }
      }
    } catch (final InterruptedException ex) {
      s_logger.info("Interrupted while waiting for subscription results.");
    } finally {
      _pendingSubscriptions.clear();
    }
    timer.finished();
  }

  private void removePendingSubscription(final ValueSpecification specification) {
    if (_pendingSubscriptions.remove(specification)) {
      notifyIfPendingSubscriptionsDone();
    }
  }

  private void removePendingSubscriptions(final Collection<ValueSpecification> specifications) {

    // Previously, this used removeAll, but as specifications may be a list, it was observed
    // that we may end up iterating over _pendingSubscriptions and calling contains() on
    // specifications, resulting in long wait times for a view to load (PLAT-3508)
    boolean removalPerformed = false;
    for (ValueSpecification specification : specifications) {
      removalPerformed = _pendingSubscriptions.remove(specification) || removalPerformed;
    }

    if (removalPerformed) {
      notifyIfPendingSubscriptionsDone();
    }
  }

  private void notifyIfPendingSubscriptionsDone() {
    if (_pendingSubscriptions.isEmpty()) {
      synchronized (_pendingSubscriptions) {
        if (_pendingSubscriptions.isEmpty()) {
          _pendingSubscriptions.notifyAll();
        }
      }
    }
  }

  private void removeMarketDataSubscriptions() {
    removeMarketDataSubscriptions(_marketDataSubscriptions);
  }

  private void removeMarketDataSubscriptions(final Collection<ValueSpecification> unusedSubscriptions) {
    final OperationTimer timer = new OperationTimer(s_logger, "Removing {} market data subscriptions", unusedSubscriptions.size());
    _marketDataProvider.unsubscribe(_marketDataSubscriptions);
    _marketDataSubscriptions.removeAll(unusedSubscriptions);
    timer.finished();
  }

  // MarketDataListener

  @Override
  public void subscriptionsSucceeded(final Collection<ValueSpecification> valueSpecifications) {
    s_logger.debug("Subscription succeeded: {}", valueSpecifications.size());
    removePendingSubscriptions(valueSpecifications);
  }

  @Override
  public void subscriptionFailed(final ValueSpecification valueSpecification, final String msg) {
    s_logger.debug("Market data subscription to {} failed. This market data may be missing from computation cycles.", valueSpecification);
    removePendingSubscription(valueSpecification);
  }

  @Override
  public void subscriptionStopped(final ValueSpecification valueSpecification) {
  }

  @Override
  public void valuesChanged(final Collection<ValueSpecification> valueSpecifications) {
    if (!getExecutionOptions().getFlags().contains(ViewExecutionFlags.TRIGGER_CYCLE_ON_MARKET_DATA_CHANGED)) {
      return;
    }
    // Don't want to query the cache for this; always use the last one
    final CompiledViewDefinitionWithGraphs compiledView = _latestCompiledViewDefinition;
    if (compiledView == null) {
      return;
    }
    if (CollectionUtils.containsAny(compiledView.getMarketDataRequirements(), valueSpecifications)) {
      requestCycle();
    }
  }

  // ViewComputationJob

  @Override
  public synchronized boolean triggerCycle() {
    s_logger.debug("Cycle triggered manually");
    _forceTriggerCycle = true;
    notifyAll();
    return true;
  }

  @Override
  public synchronized boolean requestCycle() {
    // REVIEW jonathan 2010-10-04 -- this synchronisation is necessary, but it feels very heavyweight for
    // high-frequency market data. See how it goes, but we could take into account the recalc periods and apply a
    // heuristic (e.g. only wake up due to market data if max - min < e, for some e) which tries to see whether it's
    // worth doing all this.

    _cycleRequested = true;
    if (!_wakeOnCycleRequest) {
      return true;
    }
    notifyAll();
    return true;
  }

  @Override
  public void updateViewDefinition(final ViewDefinition viewDefinition) {
    s_logger.debug("Received new view definition {} for next cycle", viewDefinition.getUniqueId());
    _newViewDefinition.getAndSet(viewDefinition);
  }

  @Override
  public void terminate() {
    getJob().terminate();
    s_logger.debug("Interrupting calculation job thread");
    getThread().interrupt();
  }

  @Override
  public void join() throws InterruptedException {
    getThread().join();
  }

  @Override
  public boolean join(final long timeout) throws InterruptedException {
    getThread().join(timeout);
    return !getThread().isAlive();
  }

  @Override
  public boolean isTerminated() {
    return getJob().isTerminated() && !getThread().isAlive();
  }

  /**
   * @deprecated DON'T USE THIS, IT'S A TEMPORARY WORKAROUND
   */
  @Deprecated
  public void forceGraphRebuild() {
    _forceGraphRebuild = true;
  }
}
