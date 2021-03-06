/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.bbg.component;

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

import com.opengamma.bbg.BloombergIdentifierProvider;
import com.opengamma.bbg.loader.hts.BloombergHistoricalTimeSeriesLoader;
import com.opengamma.bbg.referencedata.ReferenceDataProvider;
import com.opengamma.component.ComponentRepository;
import com.opengamma.component.factory.loader.AbstractHistoricalTimeSeriesLoaderComponentFactory;
import com.opengamma.master.historicaltimeseries.ExternalIdResolver;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesLoader;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesMaster;
import com.opengamma.provider.historicaltimeseries.HistoricalTimeSeriesProvider;

/**
 * Component factory providing the {@code HistoricalTimeSeriesLoader}.
 * <p>
 * This implementation uses a Bloomberg specific loader
 */
@BeanDefinition
public class BloombergHistoricalTimeSeriesLoaderComponentFactory extends AbstractHistoricalTimeSeriesLoaderComponentFactory {

  /**
   * The Bloomberg reference data provider.
   */
  @PropertyDefinition(validate = "notNull")
  private ReferenceDataProvider _referenceDataProvider;
  /**
   * The time-series master.
   */
  @PropertyDefinition(validate = "notNull")
  private HistoricalTimeSeriesMaster _historicalTimeSeriesMaster;
  /**
   * The time-series provider.
   */
  @PropertyDefinition(validate = "notNull")
  private HistoricalTimeSeriesProvider _historicalTimeSeriesProvider;

  //-------------------------------------------------------------------------
  @Override
  protected HistoricalTimeSeriesLoader createHistoricalTimeSeriesLoader(ComponentRepository repo) {
    ExternalIdResolver idProvider = new BloombergIdentifierProvider(getReferenceDataProvider());
    return new BloombergHistoricalTimeSeriesLoader(getHistoricalTimeSeriesMaster(), getHistoricalTimeSeriesProvider(), idProvider);
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code BloombergHistoricalTimeSeriesLoaderComponentFactory}.
   * @return the meta-bean, not null
   */
  public static BloombergHistoricalTimeSeriesLoaderComponentFactory.Meta meta() {
    return BloombergHistoricalTimeSeriesLoaderComponentFactory.Meta.INSTANCE;
  }
  static {
    JodaBeanUtils.registerMetaBean(BloombergHistoricalTimeSeriesLoaderComponentFactory.Meta.INSTANCE);
  }

  @Override
  public BloombergHistoricalTimeSeriesLoaderComponentFactory.Meta metaBean() {
    return BloombergHistoricalTimeSeriesLoaderComponentFactory.Meta.INSTANCE;
  }

  @Override
  protected Object propertyGet(String propertyName, boolean quiet) {
    switch (propertyName.hashCode()) {
      case -1788671322:  // referenceDataProvider
        return getReferenceDataProvider();
      case 173967376:  // historicalTimeSeriesMaster
        return getHistoricalTimeSeriesMaster();
      case -1592479713:  // historicalTimeSeriesProvider
        return getHistoricalTimeSeriesProvider();
    }
    return super.propertyGet(propertyName, quiet);
  }

  @Override
  protected void propertySet(String propertyName, Object newValue, boolean quiet) {
    switch (propertyName.hashCode()) {
      case -1788671322:  // referenceDataProvider
        setReferenceDataProvider((ReferenceDataProvider) newValue);
        return;
      case 173967376:  // historicalTimeSeriesMaster
        setHistoricalTimeSeriesMaster((HistoricalTimeSeriesMaster) newValue);
        return;
      case -1592479713:  // historicalTimeSeriesProvider
        setHistoricalTimeSeriesProvider((HistoricalTimeSeriesProvider) newValue);
        return;
    }
    super.propertySet(propertyName, newValue, quiet);
  }

  @Override
  protected void validate() {
    JodaBeanUtils.notNull(_referenceDataProvider, "referenceDataProvider");
    JodaBeanUtils.notNull(_historicalTimeSeriesMaster, "historicalTimeSeriesMaster");
    JodaBeanUtils.notNull(_historicalTimeSeriesProvider, "historicalTimeSeriesProvider");
    super.validate();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      BloombergHistoricalTimeSeriesLoaderComponentFactory other = (BloombergHistoricalTimeSeriesLoaderComponentFactory) obj;
      return JodaBeanUtils.equal(getReferenceDataProvider(), other.getReferenceDataProvider()) &&
          JodaBeanUtils.equal(getHistoricalTimeSeriesMaster(), other.getHistoricalTimeSeriesMaster()) &&
          JodaBeanUtils.equal(getHistoricalTimeSeriesProvider(), other.getHistoricalTimeSeriesProvider()) &&
          super.equals(obj);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash += hash * 31 + JodaBeanUtils.hashCode(getReferenceDataProvider());
    hash += hash * 31 + JodaBeanUtils.hashCode(getHistoricalTimeSeriesMaster());
    hash += hash * 31 + JodaBeanUtils.hashCode(getHistoricalTimeSeriesProvider());
    return hash ^ super.hashCode();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the Bloomberg reference data provider.
   * @return the value of the property, not null
   */
  public ReferenceDataProvider getReferenceDataProvider() {
    return _referenceDataProvider;
  }

  /**
   * Sets the Bloomberg reference data provider.
   * @param referenceDataProvider  the new value of the property, not null
   */
  public void setReferenceDataProvider(ReferenceDataProvider referenceDataProvider) {
    JodaBeanUtils.notNull(referenceDataProvider, "referenceDataProvider");
    this._referenceDataProvider = referenceDataProvider;
  }

  /**
   * Gets the the {@code referenceDataProvider} property.
   * @return the property, not null
   */
  public final Property<ReferenceDataProvider> referenceDataProvider() {
    return metaBean().referenceDataProvider().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the time-series master.
   * @return the value of the property, not null
   */
  public HistoricalTimeSeriesMaster getHistoricalTimeSeriesMaster() {
    return _historicalTimeSeriesMaster;
  }

  /**
   * Sets the time-series master.
   * @param historicalTimeSeriesMaster  the new value of the property, not null
   */
  public void setHistoricalTimeSeriesMaster(HistoricalTimeSeriesMaster historicalTimeSeriesMaster) {
    JodaBeanUtils.notNull(historicalTimeSeriesMaster, "historicalTimeSeriesMaster");
    this._historicalTimeSeriesMaster = historicalTimeSeriesMaster;
  }

  /**
   * Gets the the {@code historicalTimeSeriesMaster} property.
   * @return the property, not null
   */
  public final Property<HistoricalTimeSeriesMaster> historicalTimeSeriesMaster() {
    return metaBean().historicalTimeSeriesMaster().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the time-series provider.
   * @return the value of the property, not null
   */
  public HistoricalTimeSeriesProvider getHistoricalTimeSeriesProvider() {
    return _historicalTimeSeriesProvider;
  }

  /**
   * Sets the time-series provider.
   * @param historicalTimeSeriesProvider  the new value of the property, not null
   */
  public void setHistoricalTimeSeriesProvider(HistoricalTimeSeriesProvider historicalTimeSeriesProvider) {
    JodaBeanUtils.notNull(historicalTimeSeriesProvider, "historicalTimeSeriesProvider");
    this._historicalTimeSeriesProvider = historicalTimeSeriesProvider;
  }

  /**
   * Gets the the {@code historicalTimeSeriesProvider} property.
   * @return the property, not null
   */
  public final Property<HistoricalTimeSeriesProvider> historicalTimeSeriesProvider() {
    return metaBean().historicalTimeSeriesProvider().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code BloombergHistoricalTimeSeriesLoaderComponentFactory}.
   */
  public static class Meta extends AbstractHistoricalTimeSeriesLoaderComponentFactory.Meta {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code referenceDataProvider} property.
     */
    private final MetaProperty<ReferenceDataProvider> _referenceDataProvider = DirectMetaProperty.ofReadWrite(
        this, "referenceDataProvider", BloombergHistoricalTimeSeriesLoaderComponentFactory.class, ReferenceDataProvider.class);
    /**
     * The meta-property for the {@code historicalTimeSeriesMaster} property.
     */
    private final MetaProperty<HistoricalTimeSeriesMaster> _historicalTimeSeriesMaster = DirectMetaProperty.ofReadWrite(
        this, "historicalTimeSeriesMaster", BloombergHistoricalTimeSeriesLoaderComponentFactory.class, HistoricalTimeSeriesMaster.class);
    /**
     * The meta-property for the {@code historicalTimeSeriesProvider} property.
     */
    private final MetaProperty<HistoricalTimeSeriesProvider> _historicalTimeSeriesProvider = DirectMetaProperty.ofReadWrite(
        this, "historicalTimeSeriesProvider", BloombergHistoricalTimeSeriesLoaderComponentFactory.class, HistoricalTimeSeriesProvider.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> _metaPropertyMap$ = new DirectMetaPropertyMap(
        this, (DirectMetaPropertyMap) super.metaPropertyMap(),
        "referenceDataProvider",
        "historicalTimeSeriesMaster",
        "historicalTimeSeriesProvider");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1788671322:  // referenceDataProvider
          return _referenceDataProvider;
        case 173967376:  // historicalTimeSeriesMaster
          return _historicalTimeSeriesMaster;
        case -1592479713:  // historicalTimeSeriesProvider
          return _historicalTimeSeriesProvider;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends BloombergHistoricalTimeSeriesLoaderComponentFactory> builder() {
      return new DirectBeanBuilder<BloombergHistoricalTimeSeriesLoaderComponentFactory>(new BloombergHistoricalTimeSeriesLoaderComponentFactory());
    }

    @Override
    public Class<? extends BloombergHistoricalTimeSeriesLoaderComponentFactory> beanType() {
      return BloombergHistoricalTimeSeriesLoaderComponentFactory.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return _metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code referenceDataProvider} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ReferenceDataProvider> referenceDataProvider() {
      return _referenceDataProvider;
    }

    /**
     * The meta-property for the {@code historicalTimeSeriesMaster} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<HistoricalTimeSeriesMaster> historicalTimeSeriesMaster() {
      return _historicalTimeSeriesMaster;
    }

    /**
     * The meta-property for the {@code historicalTimeSeriesProvider} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<HistoricalTimeSeriesProvider> historicalTimeSeriesProvider() {
      return _historicalTimeSeriesProvider;
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
