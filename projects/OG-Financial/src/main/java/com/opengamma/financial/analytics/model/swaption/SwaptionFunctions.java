/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.financial.analytics.model.swaption;

import java.util.List;

import com.opengamma.engine.function.config.AbstractFunctionConfigurationBean;
import com.opengamma.engine.function.config.CombiningFunctionConfigurationSource;
import com.opengamma.engine.function.config.FunctionConfiguration;
import com.opengamma.engine.function.config.FunctionConfigurationSource;
import com.opengamma.financial.analytics.model.swaption.basicblack.BasicBlackFunctions;
import com.opengamma.financial.analytics.model.swaption.black.BlackFunctions;
import com.opengamma.financial.analytics.model.swaption.deprecated.DeprecatedFunctions;

/**
 * Function repository configuration source for the functions contained in this package and sub-packages.
 */
public class SwaptionFunctions extends AbstractFunctionConfigurationBean {

  /**
   * Default instance of a repository configuration source exposing the functions from this package and its sub-packages.
   *
   * @return the configuration source exposing functions from this package and its sub-packages
   */
  public static FunctionConfigurationSource instance() {
    return new SwaptionFunctions().getObjectCreating();
  }

  public static FunctionConfigurationSource deprecated() {
    return new DeprecatedFunctions().getObjectCreating();
  }

  @Override
  protected void addAllConfigurations(final List<FunctionConfiguration> functions) {
    // Nothing in this package, just the sub-packages
  }

  protected FunctionConfigurationSource blackFunctionConfiguration() {
    return BlackFunctions.instance();
  }

  protected FunctionConfigurationSource basicBlackFunctionConfiguration() {
    return BasicBlackFunctions.instance();
  }

  @Override
  protected FunctionConfigurationSource createObject() {
    return CombiningFunctionConfigurationSource.of(super.createObject(), basicBlackFunctionConfiguration(), blackFunctionConfiguration());
  }

}
