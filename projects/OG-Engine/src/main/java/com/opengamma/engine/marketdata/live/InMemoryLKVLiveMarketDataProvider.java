/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.engine.marketdata.live;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.fudgemsg.FudgeField;
import org.fudgemsg.FudgeMsg;
import org.fudgemsg.types.FudgeDate;
import org.fudgemsg.types.FudgeDateTime;
import org.fudgemsg.wire.types.FudgeWireType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.opengamma.engine.marketdata.AbstractMarketDataProvider;
import com.opengamma.engine.marketdata.InMemoryLKVMarketDataProvider;
import com.opengamma.engine.marketdata.MarketDataPermissionProvider;
import com.opengamma.engine.marketdata.MarketDataProvider;
import com.opengamma.engine.marketdata.MarketDataSnapshot;
import com.opengamma.engine.marketdata.availability.MarketDataAvailabilityFilter;
import com.opengamma.engine.marketdata.availability.MarketDataAvailabilityProvider;
import com.opengamma.engine.marketdata.spec.LiveMarketDataSpecification;
import com.opengamma.engine.marketdata.spec.MarketDataSpecification;
import com.opengamma.engine.value.ValueSpecification;
import com.opengamma.id.ExternalId;
import com.opengamma.id.ExternalScheme;
import com.opengamma.livedata.LiveDataClient;
import com.opengamma.livedata.LiveDataListener;
import com.opengamma.livedata.LiveDataSpecification;
import com.opengamma.livedata.LiveDataValueUpdate;
import com.opengamma.livedata.UserPrincipal;
import com.opengamma.livedata.msg.LiveDataSubscriptionResponse;
import com.opengamma.livedata.msg.LiveDataSubscriptionResult;
import com.opengamma.livedata.normalization.StandardRules;
import com.opengamma.util.ArgumentChecker;

/**
 * A {@link MarketDataProvider} for live data backed by an {@link InMemoryLKVMarketDataProvider}.
 */
public class InMemoryLKVLiveMarketDataProvider extends AbstractMarketDataProvider implements LiveMarketDataProvider, LiveDataListener {

  /** Logger. */
  private static final Logger s_logger = LoggerFactory.getLogger(InMemoryLKVLiveMarketDataProvider.class);

  private static final class Subscription {

    /**
     * The value specifications subscribed to this item (mapped to the open subscription count).
     */
    private Map<ValueSpecification, Integer> _values = new HashMap<ValueSpecification, Integer>();

    /**
     * The requested live data specification (inferred from a value specification), or NULL if the subscription has failed.
     */
    private volatile LiveDataSpecification _requestedLiveData;

    /**
     * The fully qualified live data (as returned by the data provider), or NULL if the subscription has not completed yet.
     */
    private LiveDataSpecification _fullyQualifiedLiveData;

    public Subscription(final ValueSpecification valueSpecification, final LiveDataSpecification liveDataSpecification) {
      _values.put(valueSpecification, 1);
      _requestedLiveData = liveDataSpecification;
    }

    public LiveDataSpecification getRequestedLiveData() {
      return _requestedLiveData;
    }

    public void setRequestedLiveData(final LiveDataSpecification liveData) {
      _requestedLiveData = liveData;
    }

    public LiveDataSpecification getFullyQualifiedLiveData() {
      return _fullyQualifiedLiveData;
    }

    public void setFullyQualifiedLiveData(final LiveDataSpecification liveData) {
      if (_requestedLiveData.equals(liveData)) {
        _fullyQualifiedLiveData = _requestedLiveData;
      } else {
        _fullyQualifiedLiveData = liveData;
      }
    }

    public void retry(final LiveDataSpecification liveData) {
      _requestedLiveData = liveData;
    }

    public void incrementCount(final ValueSpecification valueSpecification) {
      Integer count = _values.get(valueSpecification);
      if (count == null) {
        _values.put(valueSpecification, 1);
      } else {
        _values.put(valueSpecification, count + 1);
      }
    }

    public boolean decrementCount(final ValueSpecification valueSpecification) {
      Integer count = _values.get(valueSpecification);
      if (count == null) {
        return true;
      } else {
        if (count.intValue() == 1) {
          _values.remove(valueSpecification);
          return true;
        } else {
          _values.put(valueSpecification, count - 1);
          return false;
        }
      }
    }

    public boolean isUnsubscribed() {
      return _values.isEmpty();
    }

    public Collection<ValueSpecification> getValueSpecifications() {
      return _values.keySet();
    }

  }

  // Injected Inputs:
  private final LiveDataClient _liveDataClient;
  private final MarketDataAvailabilityProvider _availabilityProvider;

  // Runtime State:
  private final InMemoryLKVMarketDataProvider _underlyingProvider;
  private final MarketDataPermissionProvider _permissionProvider;
  private final ConcurrentMap<ValueSpecification, Subscription> _allSubscriptions = new ConcurrentHashMap<ValueSpecification, Subscription>();
  private final ConcurrentMap<LiveDataSpecification, Subscription> _activeSubscriptions = new ConcurrentHashMap<LiveDataSpecification, Subscription>();
  private final UserPrincipal _marketDataUser;

  public InMemoryLKVLiveMarketDataProvider(final LiveDataClient liveDataClient,
      final MarketDataAvailabilityFilter availabilityFilter,
      final UserPrincipal marketDataUser) {
    this(liveDataClient, availabilityFilter, new LiveMarketDataPermissionProvider(liveDataClient), marketDataUser);
  }

  public InMemoryLKVLiveMarketDataProvider(final LiveDataClient liveDataClient,
      final MarketDataAvailabilityFilter availabilityFilter,
      final MarketDataPermissionProvider permissionProvider,
      final UserPrincipal marketDataUser) {
    ArgumentChecker.notNull(liveDataClient, "liveDataClient");
    ArgumentChecker.notNull(availabilityFilter, "availabilityFilter");
    ArgumentChecker.notNull(permissionProvider, "permissionProvider");
    ArgumentChecker.notNull(marketDataUser, "marketDataUser");
    _liveDataClient = liveDataClient;
    // TODO: Should we use the default normalization rules from the live data client rather than hard code the standard rule set here?
    _availabilityProvider = availabilityFilter.withProvider(new LiveMarketDataAvailabilityProvider(StandardRules.getOpenGammaRuleSetId()));
    _underlyingProvider = new InMemoryLKVMarketDataProvider();
    _permissionProvider = permissionProvider;
    _marketDataUser = marketDataUser;
  }

  @Override
  public void subscribe(final ValueSpecification valueSpecification) {
    subscribe(Collections.singleton(valueSpecification));
  }

  @Override
  public void subscribe(final Set<ValueSpecification> valueSpecifications) {
    final Collection<LiveDataSpecification> toSubscribe = new ArrayList<LiveDataSpecification>(valueSpecifications.size());
    final Collection<ValueSpecification> alreadySubscribed = new ArrayList<ValueSpecification>(valueSpecifications.size());
    // Serialize all subscribes/unsubscribes
    synchronized (_liveDataClient) {
      synchronized (_activeSubscriptions) {
        for (final ValueSpecification valueSpecification : valueSpecifications) {
          Subscription subscription = _allSubscriptions.get(valueSpecification);
          if (subscription == null) {
            final LiveDataSpecification liveDataSpecification = LiveMarketDataAvailabilityProvider.getLiveDataSpecification(valueSpecification);
            subscription = _activeSubscriptions.get(liveDataSpecification);
            if (subscription == null) {
              // Start a new subscription
              subscription = new Subscription(valueSpecification, liveDataSpecification);
              _allSubscriptions.put(valueSpecification, subscription);
              _activeSubscriptions.put(liveDataSpecification, subscription);
              toSubscribe.add(liveDataSpecification);
              continue;
            }
            _allSubscriptions.put(valueSpecification, subscription);
          }
          // Increment the count on an open subscription
          subscription.incrementCount(valueSpecification);
          // If the subscription previously failed, try again -- is this the behavior we want; or should we be a bit less eager to retry?
          if (subscription.getRequestedLiveData() == null) {
            final LiveDataSpecification liveDataSpecification = LiveMarketDataAvailabilityProvider.getLiveDataSpecification(valueSpecification);
            subscription.retry(liveDataSpecification);
            toSubscribe.add(liveDataSpecification);
          } else {
            alreadySubscribed.add(valueSpecification);
          }
        }
      }
      if (!toSubscribe.isEmpty()) {
        s_logger.info("Subscribing {} to {} live data specifications", _marketDataUser, toSubscribe.size());
        _liveDataClient.subscribe(_marketDataUser, toSubscribe, this);
      }
      if (!alreadySubscribed.isEmpty()) {
        s_logger.info("Already subscribed {} to {} live data specifications", _marketDataUser, alreadySubscribed.size());
        subscriptionsSucceeded(alreadySubscribed);
      }
    }
  }

  @Override
  public void unsubscribe(final ValueSpecification valueSpecification) {
    unsubscribe(Collections.singleton(valueSpecification));
  }

  @Override
  public void unsubscribe(final Set<ValueSpecification> valueSpecifications) {
    final Set<LiveDataSpecification> liveDataSpecs = Sets.newHashSetWithExpectedSize(valueSpecifications.size());
    // Serialize all subscribes/unsubscribes 
    synchronized (_liveDataClient) {
      synchronized (_activeSubscriptions) {
        for (final ValueSpecification valueSpecification : valueSpecifications) {
          Subscription subscription = _allSubscriptions.get(valueSpecification);
          if (subscription != null) {
            if (subscription.decrementCount(valueSpecification)) {
              _allSubscriptions.remove(valueSpecification);
              if (subscription.isUnsubscribed()) {
                final LiveDataSpecification requestedLiveData = subscription.getRequestedLiveData();
                if (requestedLiveData != null) {
                  _activeSubscriptions.remove(requestedLiveData);
                  final LiveDataSpecification actualLiveData = subscription.getFullyQualifiedLiveData();
                  if (actualLiveData != null) {
                    if (actualLiveData != requestedLiveData) {
                      _activeSubscriptions.remove(actualLiveData);
                    }
                    liveDataSpecs.add(actualLiveData);
                  }
                }
              }
            }
          }
        }
      }
      if (!liveDataSpecs.isEmpty()) {
        s_logger.info("Unsubscribing {} from {} live data specifications", _marketDataUser, liveDataSpecs.size());
        _liveDataClient.unsubscribe(_marketDataUser, liveDataSpecs, this);
      }
    }
  }

  //-------------------------------------------------------------------------
  @Override
  public MarketDataAvailabilityProvider getAvailabilityProvider(final MarketDataSpecification marketDataSpec) {
    return _availabilityProvider;
  }

  @Override
  public MarketDataPermissionProvider getPermissionProvider() {
    return _permissionProvider;
  }

  //-------------------------------------------------------------------------
  @Override
  public boolean isCompatible(final MarketDataSpecification marketDataSpec) {
    // We don't look at the live data provider field at the moment
    return marketDataSpec instanceof LiveMarketDataSpecification;
  }

  @Override
  public MarketDataSnapshot snapshot(final MarketDataSpecification marketDataSpec) {
    return new LiveMarketDataSnapshot(_underlyingProvider.snapshot(marketDataSpec), this);
  }

  //-------------------------------------------------------------------------
  @Override
  public void subscriptionResultReceived(final LiveDataSubscriptionResponse subscriptionResult) {
    subscriptionResultsReceived(Collections.singleton(subscriptionResult));
  }

  @Override
  public void subscriptionResultsReceived(final Collection<LiveDataSubscriptionResponse> subscriptionResults) {
    final Set<ValueSpecification> successfulSubscriptions = new HashSet<ValueSpecification>();
    final Set<ValueSpecification> failedSubscriptions = new HashSet<ValueSpecification>();
    synchronized (_activeSubscriptions) {
      for (LiveDataSubscriptionResponse subscriptionResult : subscriptionResults) {
        final Subscription subscription = _activeSubscriptions.get(subscriptionResult.getRequestedSpecification());
        if (subscription == null) {
          s_logger.warn("Received subscription result for which there are no open subscriptions: {}", subscriptionResult);
          continue;
        }
        if (subscription.getRequestedLiveData() == null) {
          s_logger.warn("Received subscription result for already failed subscription: {}", subscriptionResult);
          continue;
        }
        if (subscription.getFullyQualifiedLiveData() != null) {
          s_logger.warn("Received duplicate subscription result: {}", subscriptionResult);
          _activeSubscriptions.remove(subscription.getFullyQualifiedLiveData());
        }
        if (subscriptionResult.getSubscriptionResult() == LiveDataSubscriptionResult.SUCCESS) {
          subscription.setFullyQualifiedLiveData(subscriptionResult.getFullyQualifiedSpecification());
          _activeSubscriptions.put(subscription.getFullyQualifiedLiveData(), subscription);
          successfulSubscriptions.addAll(subscription.getValueSpecifications());
          s_logger.debug("Subscription made to {} resulted in fully qualified {}", subscriptionResult.getRequestedSpecification(), subscriptionResult.getFullyQualifiedSpecification());
        } else {
          subscription.setRequestedLiveData(null);
          failedSubscriptions.addAll(subscription.getValueSpecifications());
          if (subscriptionResult.getSubscriptionResult() == LiveDataSubscriptionResult.NOT_AUTHORIZED) {
            s_logger.warn("Subscription to {} failed because user is not authorised: {}", subscriptionResult.getRequestedSpecification(), subscriptionResult);
          } else {
            s_logger.debug("Subscription to {} failed: {}", subscriptionResult.getRequestedSpecification(), subscriptionResult);
          }
        }
      }
    }
    s_logger.info("Subscription results - {} success, {} failures", successfulSubscriptions.size(), failedSubscriptions.size());
    if (!failedSubscriptions.isEmpty()) {
      valuesChanged(failedSubscriptions); // PLAT-1429: wake up the init call
      subscriptionFailed(failedSubscriptions, "TODO: get/concat message(s) from " + failedSubscriptions.size() + " failures"/*subscriptionResult.getUserMessage()*/);
    }
    if (!successfulSubscriptions.isEmpty()) {
      subscriptionsSucceeded(successfulSubscriptions);
    }
  }

  @Override
  public boolean isFailed(final ValueSpecification specification) {
    final Subscription subscription = _allSubscriptions.get(specification);
    if (subscription == null) {
      // No subscription - treat as a failure
      return true;
    }
    return subscription.getRequestedLiveData() == null;
  }

  @Override
  public void subscriptionStopped(final LiveDataSpecification fullyQualifiedSpecification) {
    // Ignore. We've already done our housekeeping before calling out to the liveData client.
  }

  @Override
  public void valueUpdate(final LiveDataValueUpdate valueUpdate) {
    s_logger.debug("Update received {}", valueUpdate);
    final Subscription subscriptionInfo = _activeSubscriptions.get(valueUpdate.getSpecification());
    if (subscriptionInfo == null) {
      s_logger.warn("Received value update for which no subscriptions were found: {}", valueUpdate.getSpecification());
      return;
    }
    final Collection<ValueSpecification> subscriptions = subscriptionInfo.getValueSpecifications();
    s_logger.debug("Subscribed values are {}", subscriptions);
    final FudgeMsg msg = valueUpdate.getFields();
    for (final ValueSpecification subscription : subscriptions) {
      final FudgeField field = msg.getByName(subscription.getValueName());
      if (field == null) {
        s_logger.debug("{} subscription was null, {}", subscription.getValueName(), subscription.getTargetSpecification());
      } else {
        Object value;
        switch (field.getType().getTypeId()) {
          case FudgeWireType.BYTE_TYPE_ID:
          case FudgeWireType.SHORT_TYPE_ID:
          case FudgeWireType.INT_TYPE_ID:
          case FudgeWireType.LONG_TYPE_ID:
          case FudgeWireType.FLOAT_TYPE_ID:
            // All numeric data is presented as a double downstream - convert
            value = ((Number) field.getValue()).doubleValue();
            break;
          case FudgeWireType.DOUBLE_TYPE_ID:
            // Already a double
            value = (Double) field.getValue();
            break;
          case FudgeWireType.DATE_TYPE_ID:
            value = ((FudgeDate) field.getValue()).toLocalDate();
            break;
          case FudgeWireType.DATETIME_TYPE_ID:
            value = ((FudgeDateTime) field.getValue()).toLocalDateTime();
            break;
          default:
            s_logger.warn("Unexpected market data type {}", field);
            continue;
        }
        _underlyingProvider.addValue(subscription, value);
      }
    }
    valuesChanged(subscriptions);
  }

  /**
   * Reattempts subscriptions for any data identified by the specified schemes. If a data provider becomes available
   * this method will be invoked with the schemes handled by the provider. This gives this class the opportunity
   * to reattempt previously failed subscriptions.
   * @param schemes The schemes for which market data subscriptions should be reattempted.
   */
  /* package */ void resubscribe(Set<ExternalScheme> schemes) {
    Set<ValueSpecification> valueSpecs = Sets.newHashSet();
    for (ValueSpecification valueSpec : _allSubscriptions.keySet()) {
      LiveDataSpecification liveDataSpec = LiveMarketDataAvailabilityProvider.getLiveDataSpecification(valueSpec);
      for (ExternalId id : liveDataSpec.getIdentifiers()) {
        if (schemes.contains(id.getScheme())) {
          valueSpecs.add(valueSpec);
        }
      }
    }
    subscribe(valueSpecs);
  }
}
