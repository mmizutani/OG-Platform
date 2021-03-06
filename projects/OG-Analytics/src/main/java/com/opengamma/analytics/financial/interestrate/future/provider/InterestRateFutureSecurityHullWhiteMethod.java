/**
 * Copyright (C) 2011 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.analytics.financial.interestrate.future.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.opengamma.analytics.financial.interestrate.future.derivative.InterestRateFutureSecurity;
import com.opengamma.analytics.financial.model.interestrate.HullWhiteOneFactorPiecewiseConstantInterestRateModel;
import com.opengamma.analytics.financial.provider.description.interestrate.HullWhiteOneFactorProviderInterface;
import com.opengamma.analytics.financial.provider.sensitivity.multicurve.ForwardSensitivity;
import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MulticurveSensitivity;
import com.opengamma.util.ArgumentChecker;

/**
 * Method to compute the price for an interest rate future with convexity adjustment from a Hull-White one factor model.
 * <p> Reference: Henrard M., Eurodollar Futures and Options: Convexity Adjustment in HJM One-Factor Model. March 2005. 
 * Available at <a href="http://ssrn.com/abstract=682343">http://ssrn.com/abstract=682343</a>
 */
public final class InterestRateFutureSecurityHullWhiteMethod { // extends InterestRateFutureSecurityMethod {

  /**
   * The unique instance of the calculator.
   */
  private static final InterestRateFutureSecurityHullWhiteMethod INSTANCE = new InterestRateFutureSecurityHullWhiteMethod();

  /**
   * Gets the calculator instance.
   * @return The calculator.
   */
  public static InterestRateFutureSecurityHullWhiteMethod getInstance() {
    return INSTANCE;
  }

  /**
   * Constructor.
   */
  private InterestRateFutureSecurityHullWhiteMethod() {
  }

  /**
   * The Hull-White model.
   */
  private static final HullWhiteOneFactorPiecewiseConstantInterestRateModel MODEL = new HullWhiteOneFactorPiecewiseConstantInterestRateModel();

  /**
   * Computes the price of a future from the curves using an estimation of the future rate without convexity adjustment.
   * @param future The future.
   * @param hwMulticurves The multi-curves provider with Hull-White one factor parameters.
   * @return The price.
   */
  public double price(final InterestRateFutureSecurity future, final HullWhiteOneFactorProviderInterface hwMulticurves) {
    ArgumentChecker.notNull(future, "Future");
    ArgumentChecker.notNull(hwMulticurves, "Multi-curves with Hull-White");
    double forward = hwMulticurves.getMulticurveProvider().getForwardRate(future.getIborIndex(), future.getFixingPeriodStartTime(), future.getFixingPeriodEndTime(),
        future.getFixingPeriodAccrualFactor());
    double futureConvexityFactor = MODEL.futuresConvexityFactor(hwMulticurves.getHullWhiteParameters(), future.getLastTradingTime(),
        future.getFixingPeriodStartTime(), future.getFixingPeriodEndTime());
    double price = 1.0 - futureConvexityFactor * forward + (1 - futureConvexityFactor) / future.getFixingPeriodAccrualFactor();
    return price;
  }

  /**
   * Compute the price sensitivity to rates of a interest rate future by discounting.
   * @param futures The future.
   * @param hwMulticurves The multi-curves provider with Hull-White one factor parameters.
   * @return The price rate sensitivity.
   */
  public MulticurveSensitivity priceCurveSensitivity(final InterestRateFutureSecurity futures, final HullWhiteOneFactorProviderInterface hwMulticurves) {
    ArgumentChecker.notNull(futures, "Future");
    ArgumentChecker.notNull(hwMulticurves, "Multi-curves with Hull-White");
    double futureConvexityFactor = MODEL.futuresConvexityFactor(hwMulticurves.getHullWhiteParameters(), futures.getLastTradingTime(), futures.getFixingPeriodStartTime(),
        futures.getFixingPeriodEndTime());
    // Backward sweep
    double priceBar = 1.0;
    double forwardBar = -futureConvexityFactor * priceBar;
    final Map<String, List<ForwardSensitivity>> mapFwd = new HashMap<String, List<ForwardSensitivity>>();
    final List<ForwardSensitivity> listForward = new ArrayList<ForwardSensitivity>();
    listForward.add(new ForwardSensitivity(futures.getFixingPeriodStartTime(), futures.getFixingPeriodEndTime(), futures.getFixingPeriodAccrualFactor(), forwardBar));
    mapFwd.put(hwMulticurves.getMulticurveProvider().getName(futures.getIborIndex()), listForward);
    return MulticurveSensitivity.ofForward(mapFwd);
  }

}
