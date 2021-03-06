/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.masterdb.security.hibernate.cds;

import java.util.Map;

import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.impl.direct.DirectBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

/**
 * 
 */
@BeanDefinition
public class StandardFixedRecoveryCDSSecurityBean extends StandardCDSSecurityBean {

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code StandardFixedRecoveryCDSSecurityBean}.
   * @return the meta-bean, not null
   */
  public static StandardFixedRecoveryCDSSecurityBean.Meta meta() {
    return StandardFixedRecoveryCDSSecurityBean.Meta.INSTANCE;
  }
  static {
    JodaBeanUtils.registerMetaBean(StandardFixedRecoveryCDSSecurityBean.Meta.INSTANCE);
  }

  @Override
  public StandardFixedRecoveryCDSSecurityBean.Meta metaBean() {
    return StandardFixedRecoveryCDSSecurityBean.Meta.INSTANCE;
  }

  @Override
  protected Object propertyGet(String propertyName, boolean quiet) {
    return super.propertyGet(propertyName, quiet);
  }

  @Override
  protected void propertySet(String propertyName, Object newValue, boolean quiet) {
    super.propertySet(propertyName, newValue, quiet);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      return super.equals(obj);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    return hash ^ super.hashCode();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code StandardFixedRecoveryCDSSecurityBean}.
   */
  public static class Meta extends StandardCDSSecurityBean.Meta {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> _metaPropertyMap$ = new DirectMetaPropertyMap(
        this, (DirectMetaPropertyMap) super.metaPropertyMap());

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    public BeanBuilder<? extends StandardFixedRecoveryCDSSecurityBean> builder() {
      return new DirectBeanBuilder<StandardFixedRecoveryCDSSecurityBean>(new StandardFixedRecoveryCDSSecurityBean());
    }

    @Override
    public Class<? extends StandardFixedRecoveryCDSSecurityBean> beanType() {
      return StandardFixedRecoveryCDSSecurityBean.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return _metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
