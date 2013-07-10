/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.component.factory.livedata;

import java.util.LinkedHashMap;
import java.util.Map;

import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.component.ComponentInfo;
import com.opengamma.component.ComponentRepository;
import com.opengamma.component.factory.AbstractComponentFactory;
import com.opengamma.component.factory.ComponentInfoAttributes;
import com.opengamma.livedata.entitlement.EntitlementServer;
import com.opengamma.livedata.server.HeartbeatReceiver;
import com.opengamma.livedata.server.LiveDataServer;
import com.opengamma.livedata.server.StandardLiveDataServer;
import com.opengamma.livedata.server.SubscriptionRequestReceiver;
import com.opengamma.provider.livedata.LiveDataMetaData;
import com.opengamma.provider.livedata.LiveDataMetaDataProvider;
import com.opengamma.provider.livedata.impl.DataLiveDataMetaDataProviderResource;
import com.opengamma.provider.livedata.impl.RemoteLiveDataMetaDataProvider;
import com.opengamma.provider.livedata.impl.SimpleLiveDataMetaDataProvider;
import com.opengamma.transport.FudgeRequestDispatcher;
import com.opengamma.transport.jms.JmsByteArrayMessageDispatcher;
import com.opengamma.transport.jms.JmsByteArrayRequestDispatcher;
import com.opengamma.util.jms.JmsConnector;
import com.opengamma.util.jms.JmsTopicContainer;

/**
 * Component factory to create a standard live data server.
 */
@BeanDefinition
public abstract class AbstractStandardLiveDataServerComponentFactory extends AbstractComponentFactory {

  /**
   * The classifier that the factory should publish under.
   */
  @PropertyDefinition(validate = "notNull")
  private String _classifier;
  /**
   * The flag determining whether the component should be published by REST (default true).
   */
  @PropertyDefinition
  private boolean _publishRest = true;
  /**
   * The flag determining whether the component should be published by JMS (default true).
   */
  @PropertyDefinition
  private boolean _publishJms = true;

  /**
   * The JMS connector.
   */
  @PropertyDefinition(validate = "notNull")
  private JmsConnector _jmsConnector;
  /**
   * The name of the subscription topic, null if not used.
   */
  @PropertyDefinition
  private String _jmsSubscriptionTopic;
  /**
   * The name of the entitlement topic, null if not used.
   */
  @PropertyDefinition
  private String _jmsEntitlementTopic;
  /**
   * The name of the heartbeat topic, null if not used.
   */
  @PropertyDefinition
  private String _jmsHeartbeatTopic;

  //-------------------------------------------------------------------------
  @Override
  public void init(ComponentRepository repo, LinkedHashMap<String, String> configuration) throws Exception {
    StandardLiveDataServer server = initServer(repo);
    final ComponentInfo info = new ComponentInfo(LiveDataServer.class, getClassifier());
    repo.registerComponent(info, server);

    if (isPublishJms()) {
      publishJms(repo, server);
    }
    if (isPublishRest()) {
      publishRest(repo, server);
    }
  }

  /**
   * Creates the server, without registering it.
   * <p>
   * The calling code will register it and publish via JMS and REST.
   * 
   * @param repo the repository, not null
   * @return the server, not null
   */
  protected abstract StandardLiveDataServer initServer(ComponentRepository repo);

  /**
   * Publishes the server by JMS.
   * 
   * @param repo the repository, not null
   * @param server the server, not null
   */
  protected void publishJms(ComponentRepository repo, StandardLiveDataServer server) {
    publishJmsSubscription(repo, server);
    publishJmsEntitlement(repo, server);
    publishJmsHeartbeat(repo, server);
  }

  /**
   * Publishes the JMS subscription topic.
   * 
   * @param repo the repository, not null
   * @param server the server, not null
   */
  protected void publishJmsSubscription(ComponentRepository repo, StandardLiveDataServer server) {
    SubscriptionRequestReceiver receiver = new SubscriptionRequestReceiver(server);
    FudgeRequestDispatcher dispatcher = new FudgeRequestDispatcher(receiver);
    JmsByteArrayRequestDispatcher jmsDispatcher = new JmsByteArrayRequestDispatcher(dispatcher);
    JmsTopicContainer jmsContainer = getJmsConnector().getTopicContainerFactory().create(getJmsSubscriptionTopic(), jmsDispatcher);
    repo.registerLifecycle(jmsContainer);
  }

  /**
   * Publishes the JMS entitlement topic.
   * 
   * @param repo the repository, not null
   * @param server the server, not null
   */
  protected void publishJmsEntitlement(ComponentRepository repo, StandardLiveDataServer server) {
    EntitlementServer entitlementServer = new EntitlementServer(server.getEntitlementChecker());
    FudgeRequestDispatcher dispatcher = new FudgeRequestDispatcher(entitlementServer);
    JmsByteArrayRequestDispatcher jmsDispatcher = new JmsByteArrayRequestDispatcher(dispatcher);
    JmsTopicContainer jmsContainer = getJmsConnector().getTopicContainerFactory().create(getJmsEntitlementTopic(), jmsDispatcher);
    repo.registerLifecycle(jmsContainer);
  }

  /**
   * Publishes the JMS heartbeat topic.
   * 
   * @param repo the repository, not null
   * @param server the server, not null
   */
  protected void publishJmsHeartbeat(ComponentRepository repo, StandardLiveDataServer server) {
    HeartbeatReceiver receiver = new HeartbeatReceiver(server.getExpirationManager());
    JmsByteArrayMessageDispatcher jmsDispatcher = new JmsByteArrayMessageDispatcher(receiver);
    JmsTopicContainer jmsContainer = getJmsConnector().getTopicContainerFactory().create(getJmsHeartbeatTopic(), jmsDispatcher);
    repo.registerLifecycle(jmsContainer);
  }

  /**
   * Publishes the component over REST by publishing a meta-data provider.
   * 
   * @param repo the repository, not null
   * @param server the server being produced, not null
   */
  protected void publishRest(ComponentRepository repo, StandardLiveDataServer server) {
    final LiveDataMetaData metaData = createMetaData(repo);
    metaData.setJmsBrokerUri(getJmsConnector().getClientBrokerUri());
    metaData.setJmsSubscriptionTopic(getJmsSubscriptionTopic());
    metaData.setJmsEntitlementTopic(getJmsEntitlementTopic());
    metaData.setJmsHeartbeatTopic(getJmsHeartbeatTopic());
    final LiveDataMetaDataProvider provider = new SimpleLiveDataMetaDataProvider(metaData);
    final ComponentInfo infoProvider = new ComponentInfo(LiveDataMetaDataProvider.class, getClassifier());
    infoProvider.addAttribute(ComponentInfoAttributes.LEVEL, 1);
    infoProvider.addAttribute(ComponentInfoAttributes.REMOTE_CLIENT_JAVA, RemoteLiveDataMetaDataProvider.class);
    repo.registerComponent(infoProvider, provider);
    repo.getRestComponents().publish(infoProvider, new DataLiveDataMetaDataProviderResource(provider));
  }

  /**
   * Creates the shell meta-data for REST that will have JMS details attached.
   * 
   * @param repo the repository, not null
   * @return the meta-data, not null
   */
  protected abstract LiveDataMetaData createMetaData(ComponentRepository repo);

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code AbstractStandardLiveDataServerComponentFactory}.
   * @return the meta-bean, not null
   */
  public static AbstractStandardLiveDataServerComponentFactory.Meta meta() {
    return AbstractStandardLiveDataServerComponentFactory.Meta.INSTANCE;
  }
  static {
    JodaBeanUtils.registerMetaBean(AbstractStandardLiveDataServerComponentFactory.Meta.INSTANCE);
  }

  @Override
  public AbstractStandardLiveDataServerComponentFactory.Meta metaBean() {
    return AbstractStandardLiveDataServerComponentFactory.Meta.INSTANCE;
  }

  @Override
  protected Object propertyGet(String propertyName, boolean quiet) {
    switch (propertyName.hashCode()) {
      case -281470431:  // classifier
        return getClassifier();
      case -614707837:  // publishRest
        return isPublishRest();
      case 1919825921:  // publishJms
        return isPublishJms();
      case -1495762275:  // jmsConnector
        return getJmsConnector();
      case -102439838:  // jmsSubscriptionTopic
        return getJmsSubscriptionTopic();
      case -59808846:  // jmsEntitlementTopic
        return getJmsEntitlementTopic();
      case -326199997:  // jmsHeartbeatTopic
        return getJmsHeartbeatTopic();
    }
    return super.propertyGet(propertyName, quiet);
  }

  @Override
  protected void propertySet(String propertyName, Object newValue, boolean quiet) {
    switch (propertyName.hashCode()) {
      case -281470431:  // classifier
        setClassifier((String) newValue);
        return;
      case -614707837:  // publishRest
        setPublishRest((Boolean) newValue);
        return;
      case 1919825921:  // publishJms
        setPublishJms((Boolean) newValue);
        return;
      case -1495762275:  // jmsConnector
        setJmsConnector((JmsConnector) newValue);
        return;
      case -102439838:  // jmsSubscriptionTopic
        setJmsSubscriptionTopic((String) newValue);
        return;
      case -59808846:  // jmsEntitlementTopic
        setJmsEntitlementTopic((String) newValue);
        return;
      case -326199997:  // jmsHeartbeatTopic
        setJmsHeartbeatTopic((String) newValue);
        return;
    }
    super.propertySet(propertyName, newValue, quiet);
  }

  @Override
  protected void validate() {
    JodaBeanUtils.notNull(_classifier, "classifier");
    JodaBeanUtils.notNull(_jmsConnector, "jmsConnector");
    super.validate();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      AbstractStandardLiveDataServerComponentFactory other = (AbstractStandardLiveDataServerComponentFactory) obj;
      return JodaBeanUtils.equal(getClassifier(), other.getClassifier()) &&
          JodaBeanUtils.equal(isPublishRest(), other.isPublishRest()) &&
          JodaBeanUtils.equal(isPublishJms(), other.isPublishJms()) &&
          JodaBeanUtils.equal(getJmsConnector(), other.getJmsConnector()) &&
          JodaBeanUtils.equal(getJmsSubscriptionTopic(), other.getJmsSubscriptionTopic()) &&
          JodaBeanUtils.equal(getJmsEntitlementTopic(), other.getJmsEntitlementTopic()) &&
          JodaBeanUtils.equal(getJmsHeartbeatTopic(), other.getJmsHeartbeatTopic()) &&
          super.equals(obj);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash += hash * 31 + JodaBeanUtils.hashCode(getClassifier());
    hash += hash * 31 + JodaBeanUtils.hashCode(isPublishRest());
    hash += hash * 31 + JodaBeanUtils.hashCode(isPublishJms());
    hash += hash * 31 + JodaBeanUtils.hashCode(getJmsConnector());
    hash += hash * 31 + JodaBeanUtils.hashCode(getJmsSubscriptionTopic());
    hash += hash * 31 + JodaBeanUtils.hashCode(getJmsEntitlementTopic());
    hash += hash * 31 + JodaBeanUtils.hashCode(getJmsHeartbeatTopic());
    return hash ^ super.hashCode();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the classifier that the factory should publish under.
   * @return the value of the property, not null
   */
  public String getClassifier() {
    return _classifier;
  }

  /**
   * Sets the classifier that the factory should publish under.
   * @param classifier  the new value of the property, not null
   */
  public void setClassifier(String classifier) {
    JodaBeanUtils.notNull(classifier, "classifier");
    this._classifier = classifier;
  }

  /**
   * Gets the the {@code classifier} property.
   * @return the property, not null
   */
  public final Property<String> classifier() {
    return metaBean().classifier().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the flag determining whether the component should be published by REST (default true).
   * @return the value of the property
   */
  public boolean isPublishRest() {
    return _publishRest;
  }

  /**
   * Sets the flag determining whether the component should be published by REST (default true).
   * @param publishRest  the new value of the property
   */
  public void setPublishRest(boolean publishRest) {
    this._publishRest = publishRest;
  }

  /**
   * Gets the the {@code publishRest} property.
   * @return the property, not null
   */
  public final Property<Boolean> publishRest() {
    return metaBean().publishRest().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the flag determining whether the component should be published by JMS (default true).
   * @return the value of the property
   */
  public boolean isPublishJms() {
    return _publishJms;
  }

  /**
   * Sets the flag determining whether the component should be published by JMS (default true).
   * @param publishJms  the new value of the property
   */
  public void setPublishJms(boolean publishJms) {
    this._publishJms = publishJms;
  }

  /**
   * Gets the the {@code publishJms} property.
   * @return the property, not null
   */
  public final Property<Boolean> publishJms() {
    return metaBean().publishJms().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the JMS connector.
   * @return the value of the property, not null
   */
  public JmsConnector getJmsConnector() {
    return _jmsConnector;
  }

  /**
   * Sets the JMS connector.
   * @param jmsConnector  the new value of the property, not null
   */
  public void setJmsConnector(JmsConnector jmsConnector) {
    JodaBeanUtils.notNull(jmsConnector, "jmsConnector");
    this._jmsConnector = jmsConnector;
  }

  /**
   * Gets the the {@code jmsConnector} property.
   * @return the property, not null
   */
  public final Property<JmsConnector> jmsConnector() {
    return metaBean().jmsConnector().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the name of the subscription topic, null if not used.
   * @return the value of the property
   */
  public String getJmsSubscriptionTopic() {
    return _jmsSubscriptionTopic;
  }

  /**
   * Sets the name of the subscription topic, null if not used.
   * @param jmsSubscriptionTopic  the new value of the property
   */
  public void setJmsSubscriptionTopic(String jmsSubscriptionTopic) {
    this._jmsSubscriptionTopic = jmsSubscriptionTopic;
  }

  /**
   * Gets the the {@code jmsSubscriptionTopic} property.
   * @return the property, not null
   */
  public final Property<String> jmsSubscriptionTopic() {
    return metaBean().jmsSubscriptionTopic().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the name of the entitlement topic, null if not used.
   * @return the value of the property
   */
  public String getJmsEntitlementTopic() {
    return _jmsEntitlementTopic;
  }

  /**
   * Sets the name of the entitlement topic, null if not used.
   * @param jmsEntitlementTopic  the new value of the property
   */
  public void setJmsEntitlementTopic(String jmsEntitlementTopic) {
    this._jmsEntitlementTopic = jmsEntitlementTopic;
  }

  /**
   * Gets the the {@code jmsEntitlementTopic} property.
   * @return the property, not null
   */
  public final Property<String> jmsEntitlementTopic() {
    return metaBean().jmsEntitlementTopic().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the name of the heartbeat topic, null if not used.
   * @return the value of the property
   */
  public String getJmsHeartbeatTopic() {
    return _jmsHeartbeatTopic;
  }

  /**
   * Sets the name of the heartbeat topic, null if not used.
   * @param jmsHeartbeatTopic  the new value of the property
   */
  public void setJmsHeartbeatTopic(String jmsHeartbeatTopic) {
    this._jmsHeartbeatTopic = jmsHeartbeatTopic;
  }

  /**
   * Gets the the {@code jmsHeartbeatTopic} property.
   * @return the property, not null
   */
  public final Property<String> jmsHeartbeatTopic() {
    return metaBean().jmsHeartbeatTopic().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code AbstractStandardLiveDataServerComponentFactory}.
   */
  public static class Meta extends AbstractComponentFactory.Meta {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code classifier} property.
     */
    private final MetaProperty<String> _classifier = DirectMetaProperty.ofReadWrite(
        this, "classifier", AbstractStandardLiveDataServerComponentFactory.class, String.class);
    /**
     * The meta-property for the {@code publishRest} property.
     */
    private final MetaProperty<Boolean> _publishRest = DirectMetaProperty.ofReadWrite(
        this, "publishRest", AbstractStandardLiveDataServerComponentFactory.class, Boolean.TYPE);
    /**
     * The meta-property for the {@code publishJms} property.
     */
    private final MetaProperty<Boolean> _publishJms = DirectMetaProperty.ofReadWrite(
        this, "publishJms", AbstractStandardLiveDataServerComponentFactory.class, Boolean.TYPE);
    /**
     * The meta-property for the {@code jmsConnector} property.
     */
    private final MetaProperty<JmsConnector> _jmsConnector = DirectMetaProperty.ofReadWrite(
        this, "jmsConnector", AbstractStandardLiveDataServerComponentFactory.class, JmsConnector.class);
    /**
     * The meta-property for the {@code jmsSubscriptionTopic} property.
     */
    private final MetaProperty<String> _jmsSubscriptionTopic = DirectMetaProperty.ofReadWrite(
        this, "jmsSubscriptionTopic", AbstractStandardLiveDataServerComponentFactory.class, String.class);
    /**
     * The meta-property for the {@code jmsEntitlementTopic} property.
     */
    private final MetaProperty<String> _jmsEntitlementTopic = DirectMetaProperty.ofReadWrite(
        this, "jmsEntitlementTopic", AbstractStandardLiveDataServerComponentFactory.class, String.class);
    /**
     * The meta-property for the {@code jmsHeartbeatTopic} property.
     */
    private final MetaProperty<String> _jmsHeartbeatTopic = DirectMetaProperty.ofReadWrite(
        this, "jmsHeartbeatTopic", AbstractStandardLiveDataServerComponentFactory.class, String.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> _metaPropertyMap$ = new DirectMetaPropertyMap(
        this, (DirectMetaPropertyMap) super.metaPropertyMap(),
        "classifier",
        "publishRest",
        "publishJms",
        "jmsConnector",
        "jmsSubscriptionTopic",
        "jmsEntitlementTopic",
        "jmsHeartbeatTopic");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -281470431:  // classifier
          return _classifier;
        case -614707837:  // publishRest
          return _publishRest;
        case 1919825921:  // publishJms
          return _publishJms;
        case -1495762275:  // jmsConnector
          return _jmsConnector;
        case -102439838:  // jmsSubscriptionTopic
          return _jmsSubscriptionTopic;
        case -59808846:  // jmsEntitlementTopic
          return _jmsEntitlementTopic;
        case -326199997:  // jmsHeartbeatTopic
          return _jmsHeartbeatTopic;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends AbstractStandardLiveDataServerComponentFactory> builder() {
      throw new UnsupportedOperationException("AbstractStandardLiveDataServerComponentFactory is an abstract class");
    }

    @Override
    public Class<? extends AbstractStandardLiveDataServerComponentFactory> beanType() {
      return AbstractStandardLiveDataServerComponentFactory.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return _metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code classifier} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> classifier() {
      return _classifier;
    }

    /**
     * The meta-property for the {@code publishRest} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Boolean> publishRest() {
      return _publishRest;
    }

    /**
     * The meta-property for the {@code publishJms} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Boolean> publishJms() {
      return _publishJms;
    }

    /**
     * The meta-property for the {@code jmsConnector} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<JmsConnector> jmsConnector() {
      return _jmsConnector;
    }

    /**
     * The meta-property for the {@code jmsSubscriptionTopic} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> jmsSubscriptionTopic() {
      return _jmsSubscriptionTopic;
    }

    /**
     * The meta-property for the {@code jmsEntitlementTopic} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> jmsEntitlementTopic() {
      return _jmsEntitlementTopic;
    }

    /**
     * The meta-property for the {@code jmsHeartbeatTopic} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> jmsHeartbeatTopic() {
      return _jmsHeartbeatTopic;
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
