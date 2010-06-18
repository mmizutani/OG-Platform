/**
 * Copyright (C) 2009 - 2010 by OpenGamma Inc.
 *
 * Please see distribution for license.
 */
package com.opengamma.engine.position;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.Timer;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.id.UniqueIdentifier;
import com.opengamma.util.ArgumentChecker;
import com.opengamma.util.EHCacheUtils;
import com.opengamma.util.ehcache.AbstractCacheEventListener;
import com.opengamma.util.ehcache.ExpiryTaskExtension;

/**
 * A mutable {@link PositionMaster} which associates each {Portfolio} with an owner, an arbitrary object of type
 * <code>O</code>. Multiple {@link Portfolio}s may be associated with the same owner.
 * <p>
 * The {@link Portfolio} structures associated with a particular owner will eventually expire unless their lifetime is
 * periodically extended by providing a heartbeat for the owner. Internally uses an {@link Ehcache} and a
 * {@link Timer}; a {@link CacheManager} and the {@link Timer} may be specified in the constructor. 
 * <p>
 * For example, this could be used where external clients need to work with temporary {@link Portfolio}s. The owner
 * would identify a client, and the onus would be on the client to keep its session alive by providing heartbeats. If a
 * client dies then its associated {@link Portfolio} structures will be eventually removed automatically.
 * 
 * @param <O>  the type of the owner to be associated with each {@link Portfolio}
 */
public class ExpiringInMemoryPositionMaster<O> implements PositionMaster {
  
  /**
   * The scheme to use for {@link UniqueIdentifier}s assigned to portfolio structures which are added to this
   * {@link PositionMaster}.
   */
  public static final String UID_SCHEME = "ExpiringMemory";
  /**
   * The default period of the task to check for expired owners, in seconds.
   */
  public static final long DEFAULT_EXPIRY_CHECK_PERIOD = 60;
  private static final String OWNER_CACHE = "position-master-owner-cache";
  private static final int ONE_SECOND_MILLIS = 1000;
  
  private final InMemoryPositionMaster _underlying = new InMemoryPositionMaster(UID_SCHEME);
  private final Ehcache _expiringOwnerCache;
  private long _nextPortfolioUidValue;
  
  /**
   * Constructs a new {@link ExpiringInMemoryPositionMaster} using the default period for removing expired
   * {@link Portfolio}s and the {@link CacheManager} singleton.
   * 
   * @param expireAfterSeconds  the period after which {@link Portfolio}s are eligible to be removed if the owner has
   *                            failed to heartbeat
   * @param timer  the timer to use internally
   */
  public ExpiringInMemoryPositionMaster(long expireAfterSeconds, Timer timer) {
    this(expireAfterSeconds, DEFAULT_EXPIRY_CHECK_PERIOD, EHCacheUtils.createCacheManager(), timer);
  }
  
  /**
   * Constructs a new {@link ExpiringInMemoryPositionMaster}.
   * 
   * @param expireAfterSeconds  the period after which {@link Portfolio}s are eligible to be removed if the owner has
   *                            failed to heartbeat
   * @param expiryCheckPeriodSeconds  the period at which any expired {@link Portfolio}s are removed
   * @param cacheManager  the {@link CacheManager} with which to associate the internal cache
   * @param timer  the timer on which the task to remove old {@link Portfolio}s will be scheduled
   */
  public ExpiringInMemoryPositionMaster(long expireAfterSeconds, long expiryCheckPeriodSeconds, CacheManager cacheManager, Timer timer) {       
    // REVIEW jonathan 2010-06-18 -- we're using an old version of Ehcache which doesn't support an unlimited
    // maxElementsInMemory value. The docs suggest using 0 but this really means 0 with our version. For now, I'm using
    // just using a fairly large value.
    _expiringOwnerCache = new Cache(OWNER_CACHE, 10000, MemoryStoreEvictionPolicy.LRU, false, null, false, 0, expireAfterSeconds, false, 0, null);
    
    // Ehcache does not have an expiry thread for the MemoryStore, so this extension does the job. Otherwise, the cache
    // relies on accesses to evict old items. 
    _expiringOwnerCache.registerCacheExtension(new ExpiryTaskExtension(_expiringOwnerCache, timer, expiryCheckPeriodSeconds * ONE_SECOND_MILLIS));
    
    _expiringOwnerCache.getCacheEventNotificationService().registerListener(new AbstractCacheEventListener() {  

      @SuppressWarnings("unchecked")
      @Override
      public void notifyElementExpired(Ehcache cache, Element element) {
        expired((O) element.getObjectKey(), (Collection<Portfolio>) element.getObjectValue());
      }
      
    });
    
    cacheManager.addCache(_expiringOwnerCache);
  }
  
  //--------------------------------------------------------------------------
  // PositionMaster
  
  @Override
  public Portfolio getPortfolio(UniqueIdentifier uid) {
    return _underlying.getPortfolio(uid);
  }

  @Override
  public Set<UniqueIdentifier> getPortfolioIds() {
    return _underlying.getPortfolioIds();
  }

  @Override
  public PortfolioNode getPortfolioNode(UniqueIdentifier uid) {
    return _underlying.getPortfolioNode(uid);
  }

  @Override
  public Position getPosition(UniqueIdentifier uid) {
    return _underlying.getPosition(uid);
  }
  
  //--------------------------------------------------------------------------
  // PositionMaster mutability

  /**
   * Adds a {@link Portfolio} to the master.
   * 
   * @param owner  the owner with which to associate the portfolio. This owner must remain alive in order for the
   *               portfolio to be retained in the master.
   * @param portfolio  the portfolio to add, not null
   */
  @SuppressWarnings("unchecked")
  public void addPortfolio(O owner, Portfolio portfolio) {
    ArgumentChecker.notNull(portfolio, "portfolio");
    
    if (portfolio.getUniqueIdentifier() != null) {
      throw new OpenGammaRuntimeException("Attempted to add a portfolio which already belongs to a PositionMaster");
    }
    
    // Need to synchronize here to prevent race conditions with get/put on the cache, and to synchronize list writes if
    // multiple portfolios are being added to the same owner concurrently.
    synchronized (_expiringOwnerCache) {
      if (portfolio instanceof PortfolioImpl) {
        PortfolioImpl portfolioImpl = (PortfolioImpl) portfolio;
        UniqueIdentifier portfolioUid = UniqueIdentifier.of(UID_SCHEME, Long.toString(_nextPortfolioUidValue++));
        portfolioImpl.setUniqueIdentifier(portfolioUid);
      }
      Element cacheElement = _expiringOwnerCache.get(owner);
      if (cacheElement == null) {
        cacheElement = new Element(owner, new ArrayList<Portfolio>());
        _expiringOwnerCache.put(cacheElement);
      }
      Collection<Portfolio> portfolios = (Collection<Portfolio>) cacheElement.getObjectValue();
      portfolios.add(portfolio);
      _underlying.addPortfolio(portfolio);
    }
  }
  
  //--------------------------------------------------------------------------
  // Expiry-related
  
  /**
   * Indicates that the specified owner is alive. If the owner is not known to the {@link PositionMaster} then this
   * has no effect.
   * 
   * @param owner  the owner
   * @return <code>false</code> if the specified owner is not known to the {@link PositionMaster} (or has already been
   *         expired), <code>true</code> otherwise.
   */
  public boolean heartbeat(O owner) {
    // Force the last access time to be updated
    return _expiringOwnerCache.get(owner) != null;
  }
  
  /**
   * Called when an owner has expired.
   * 
   * @param owner  the expired owner
   * @param portfolios  the list of portfolios associated with the expired owner
   */
  private void expired(O owner, Collection<Portfolio> portfolios) {
    // No need to synchronize with addPortfolio because we can't be adding one of the expired portfolios again
    // concurrently (they already have IDs) and the underlying PositionMaster is thread-safe.
    for (Portfolio portfolio : portfolios) {
      _underlying.removePortfolio(portfolio);
    }
  }
  
}
