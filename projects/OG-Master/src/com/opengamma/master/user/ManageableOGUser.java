/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.master.user;

import java.io.Serializable;
import java.util.Map;

import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectBean;
import org.joda.beans.impl.direct.DirectBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.core.user.OGUser;
import com.opengamma.id.ExternalIdBundle;
import com.opengamma.id.UniqueId;
import com.opengamma.util.PublicSPI;

/**
 * Any user known to the OpenGamma Platform installation.
 */
@PublicSPI
@BeanDefinition
public class ManageableOGUser extends DirectBean implements OGUser, Serializable {
  /** Serialization version. */
  private static final long serialVersionUID = 1L;

  /**
   * The unique identifier of the user.
   * This must be null when adding to a master and not null when retrieved from a master.
   */
  @PropertyDefinition
  private UniqueId _uniqueId;
  /**
   * The bundle of external identifiers that define the user.
   * This field must not be null for the object to be valid.
   */
  @PropertyDefinition
  private ExternalIdBundle _externalIdBundle = ExternalIdBundle.EMPTY;
  /**
   * The name of the user intended for display purposes.
   * This field must not be null for the object to be valid.
   */
  @PropertyDefinition
  private String _name;

  /**
   * Returns an independent clone of this exchange.
   * 
   * @return the clone, not null
   */
  public ManageableOGUser clone() {
    ManageableOGUser cloned = new ManageableOGUser();
    cloned._uniqueId = _uniqueId;
    cloned._name = _name;
    cloned._externalIdBundle = _externalIdBundle;
    return cloned;
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code ManageableOGUser}.
   * @return the meta-bean, not null
   */
  public static ManageableOGUser.Meta meta() {
    return ManageableOGUser.Meta.INSTANCE;
  }
  static {
    JodaBeanUtils.registerMetaBean(ManageableOGUser.Meta.INSTANCE);
  }

  @Override
  public ManageableOGUser.Meta metaBean() {
    return ManageableOGUser.Meta.INSTANCE;
  }

  @Override
  protected Object propertyGet(String propertyName, boolean quiet) {
    switch (propertyName.hashCode()) {
      case -294460212:  // uniqueId
        return getUniqueId();
      case -736922008:  // externalIdBundle
        return getExternalIdBundle();
      case 3373707:  // name
        return getName();
    }
    return super.propertyGet(propertyName, quiet);
  }

  @Override
  protected void propertySet(String propertyName, Object newValue, boolean quiet) {
    switch (propertyName.hashCode()) {
      case -294460212:  // uniqueId
        setUniqueId((UniqueId) newValue);
        return;
      case -736922008:  // externalIdBundle
        setExternalIdBundle((ExternalIdBundle) newValue);
        return;
      case 3373707:  // name
        setName((String) newValue);
        return;
    }
    super.propertySet(propertyName, newValue, quiet);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      ManageableOGUser other = (ManageableOGUser) obj;
      return JodaBeanUtils.equal(getUniqueId(), other.getUniqueId()) &&
          JodaBeanUtils.equal(getExternalIdBundle(), other.getExternalIdBundle()) &&
          JodaBeanUtils.equal(getName(), other.getName());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash += hash * 31 + JodaBeanUtils.hashCode(getUniqueId());
    hash += hash * 31 + JodaBeanUtils.hashCode(getExternalIdBundle());
    hash += hash * 31 + JodaBeanUtils.hashCode(getName());
    return hash;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the unique identifier of the user.
   * This must be null when adding to a master and not null when retrieved from a master.
   * @return the value of the property
   */
  public UniqueId getUniqueId() {
    return _uniqueId;
  }

  /**
   * Sets the unique identifier of the user.
   * This must be null when adding to a master and not null when retrieved from a master.
   * @param uniqueId  the new value of the property
   */
  public void setUniqueId(UniqueId uniqueId) {
    this._uniqueId = uniqueId;
  }

  /**
   * Gets the the {@code uniqueId} property.
   * This must be null when adding to a master and not null when retrieved from a master.
   * @return the property, not null
   */
  public final Property<UniqueId> uniqueId() {
    return metaBean().uniqueId().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the bundle of external identifiers that define the user.
   * This field must not be null for the object to be valid.
   * @return the value of the property
   */
  public ExternalIdBundle getExternalIdBundle() {
    return _externalIdBundle;
  }

  /**
   * Sets the bundle of external identifiers that define the user.
   * This field must not be null for the object to be valid.
   * @param externalIdBundle  the new value of the property
   */
  public void setExternalIdBundle(ExternalIdBundle externalIdBundle) {
    this._externalIdBundle = externalIdBundle;
  }

  /**
   * Gets the the {@code externalIdBundle} property.
   * This field must not be null for the object to be valid.
   * @return the property, not null
   */
  public final Property<ExternalIdBundle> externalIdBundle() {
    return metaBean().externalIdBundle().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the name of the user intended for display purposes.
   * This field must not be null for the object to be valid.
   * @return the value of the property
   */
  public String getName() {
    return _name;
  }

  /**
   * Sets the name of the user intended for display purposes.
   * This field must not be null for the object to be valid.
   * @param name  the new value of the property
   */
  public void setName(String name) {
    this._name = name;
  }

  /**
   * Gets the the {@code name} property.
   * This field must not be null for the object to be valid.
   * @return the property, not null
   */
  public final Property<String> name() {
    return metaBean().name().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code ManageableOGUser}.
   */
  public static class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code uniqueId} property.
     */
    private final MetaProperty<UniqueId> _uniqueId = DirectMetaProperty.ofReadWrite(
        this, "uniqueId", ManageableOGUser.class, UniqueId.class);
    /**
     * The meta-property for the {@code externalIdBundle} property.
     */
    private final MetaProperty<ExternalIdBundle> _externalIdBundle = DirectMetaProperty.ofReadWrite(
        this, "externalIdBundle", ManageableOGUser.class, ExternalIdBundle.class);
    /**
     * The meta-property for the {@code name} property.
     */
    private final MetaProperty<String> _name = DirectMetaProperty.ofReadWrite(
        this, "name", ManageableOGUser.class, String.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> _metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "uniqueId",
        "externalIdBundle",
        "name");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -294460212:  // uniqueId
          return _uniqueId;
        case -736922008:  // externalIdBundle
          return _externalIdBundle;
        case 3373707:  // name
          return _name;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends ManageableOGUser> builder() {
      return new DirectBeanBuilder<ManageableOGUser>(new ManageableOGUser());
    }

    @Override
    public Class<? extends ManageableOGUser> beanType() {
      return ManageableOGUser.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return _metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code uniqueId} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<UniqueId> uniqueId() {
      return _uniqueId;
    }

    /**
     * The meta-property for the {@code externalIdBundle} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ExternalIdBundle> externalIdBundle() {
      return _externalIdBundle;
    }

    /**
     * The meta-property for the {@code name} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> name() {
      return _name;
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
