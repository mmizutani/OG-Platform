/**
 * Copyright (C) 2013 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.financial.analytics.curve;

import java.util.Map;

import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

/**
 * 
 */
@BeanDefinition
public class OvernightCurveConfiguration extends CurveConfiguration {

  /** Serialization version */
  private static final long serialVersionUID = 1L;

  /**
   * The curve name.
   */
  @PropertyDefinition(validate = "notNull")
  private String _curveName;

  /**
   * The calculation configuration name
   */
  @PropertyDefinition(validate = "notNull")
  private String _calculationConfigurationName;

  /* package */OvernightCurveConfiguration() {
  }

  public OvernightCurveConfiguration(final String curveName, final String calculationConfigurationName) {
    setCurveName(curveName);
    setCalculationConfigurationName(calculationConfigurationName);
  }

  @Override
  public <T> T accept(final CurveConfigurationVisitor<T> visitor) {
    return visitor.visitOvernightCurveConfiguration(this);
  }
  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code OvernightCurveConfiguration}.
   * @return the meta-bean, not null
   */
  public static OvernightCurveConfiguration.Meta meta() {
    return OvernightCurveConfiguration.Meta.INSTANCE;
  }
  static {
    JodaBeanUtils.registerMetaBean(OvernightCurveConfiguration.Meta.INSTANCE);
  }

  @Override
  public OvernightCurveConfiguration.Meta metaBean() {
    return OvernightCurveConfiguration.Meta.INSTANCE;
  }

  @Override
  protected Object propertyGet(String propertyName, boolean quiet) {
    switch (propertyName.hashCode()) {
      case 771153946:  // curveName
        return getCurveName();
      case -2008701736:  // calculationConfigurationName
        return getCalculationConfigurationName();
    }
    return super.propertyGet(propertyName, quiet);
  }

  @Override
  protected void propertySet(String propertyName, Object newValue, boolean quiet) {
    switch (propertyName.hashCode()) {
      case 771153946:  // curveName
        setCurveName((String) newValue);
        return;
      case -2008701736:  // calculationConfigurationName
        setCalculationConfigurationName((String) newValue);
        return;
    }
    super.propertySet(propertyName, newValue, quiet);
  }

  @Override
  protected void validate() {
    JodaBeanUtils.notNull(_curveName, "curveName");
    JodaBeanUtils.notNull(_calculationConfigurationName, "calculationConfigurationName");
    super.validate();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      OvernightCurveConfiguration other = (OvernightCurveConfiguration) obj;
      return JodaBeanUtils.equal(getCurveName(), other.getCurveName()) &&
          JodaBeanUtils.equal(getCalculationConfigurationName(), other.getCalculationConfigurationName()) &&
          super.equals(obj);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash += hash * 31 + JodaBeanUtils.hashCode(getCurveName());
    hash += hash * 31 + JodaBeanUtils.hashCode(getCalculationConfigurationName());
    return hash ^ super.hashCode();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the curve name.
   * @return the value of the property, not null
   */
  public String getCurveName() {
    return _curveName;
  }

  /**
   * Sets the curve name.
   * @param curveName  the new value of the property, not null
   */
  public void setCurveName(String curveName) {
    JodaBeanUtils.notNull(curveName, "curveName");
    this._curveName = curveName;
  }

  /**
   * Gets the the {@code curveName} property.
   * @return the property, not null
   */
  public final Property<String> curveName() {
    return metaBean().curveName().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the calculation configuration name
   * @return the value of the property, not null
   */
  public String getCalculationConfigurationName() {
    return _calculationConfigurationName;
  }

  /**
   * Sets the calculation configuration name
   * @param calculationConfigurationName  the new value of the property, not null
   */
  public void setCalculationConfigurationName(String calculationConfigurationName) {
    JodaBeanUtils.notNull(calculationConfigurationName, "calculationConfigurationName");
    this._calculationConfigurationName = calculationConfigurationName;
  }

  /**
   * Gets the the {@code calculationConfigurationName} property.
   * @return the property, not null
   */
  public final Property<String> calculationConfigurationName() {
    return metaBean().calculationConfigurationName().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code OvernightCurveConfiguration}.
   */
  public static class Meta extends CurveConfiguration.Meta {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code curveName} property.
     */
    private final MetaProperty<String> _curveName = DirectMetaProperty.ofReadWrite(
        this, "curveName", OvernightCurveConfiguration.class, String.class);
    /**
     * The meta-property for the {@code calculationConfigurationName} property.
     */
    private final MetaProperty<String> _calculationConfigurationName = DirectMetaProperty.ofReadWrite(
        this, "calculationConfigurationName", OvernightCurveConfiguration.class, String.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> _metaPropertyMap$ = new DirectMetaPropertyMap(
        this, (DirectMetaPropertyMap) super.metaPropertyMap(),
        "curveName",
        "calculationConfigurationName");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 771153946:  // curveName
          return _curveName;
        case -2008701736:  // calculationConfigurationName
          return _calculationConfigurationName;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends OvernightCurveConfiguration> builder() {
      return new DirectBeanBuilder<OvernightCurveConfiguration>(new OvernightCurveConfiguration());
    }

    @Override
    public Class<? extends OvernightCurveConfiguration> beanType() {
      return OvernightCurveConfiguration.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return _metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code curveName} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> curveName() {
      return _curveName;
    }

    /**
     * The meta-property for the {@code calculationConfigurationName} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> calculationConfigurationName() {
      return _calculationConfigurationName;
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
