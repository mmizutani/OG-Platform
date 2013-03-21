/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.financial.aggregation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import com.opengamma.core.id.ExternalSchemes;
import com.opengamma.core.organization.Organization;
import com.opengamma.core.organization.OrganizationSource;
import com.opengamma.core.position.Position;
import com.opengamma.core.position.impl.SimplePositionComparator;
import com.opengamma.core.security.SecuritySource;
import com.opengamma.financial.security.FinancialSecurity;
import com.opengamma.financial.security.FinancialSecurityVisitor;
import com.opengamma.financial.security.FinancialSecurityVisitorAdapter;
import com.opengamma.financial.security.cds.StandardVanillaCDSSecurity;
import com.opengamma.financial.security.equity.EquitySecurity;
import com.opengamma.financial.security.equity.GICSCode;
import com.opengamma.financial.security.option.EquityIndexOptionSecurity;
import com.opengamma.financial.security.option.EquityOptionSecurity;
import com.opengamma.id.ExternalId;
import com.opengamma.id.ExternalIdBundle;

/**
 * Abstract aggregation function for bucketing equities and equity options by GICS code of the underlying
 */
public class GICSAggregationFunction implements AggregationFunction<String> {

  private static final String UNKNOWN = "Unknown";
  private boolean _useAttributes;
  private final Comparator<Position> _comparator = new SimplePositionComparator();

  /**
   * Enumerated type representing how specific the GICS code should be interpreted.
   */
  public enum Level {

    /**
     * Sector
     */
    SECTOR("Sector"),

    /**
     * Industry Group
     */
    INDUSTRY_GROUP("Industry Group"),

    /**
     * Industry
     */
    INDUSTRY("Industry"),

    /**
     * Sub-industry
     */
    SUB_INDUSTRY("Sub-industry");

    private final String _displayName;

    private Level(String displayName) {
      _displayName = displayName;
    }

    public String getDisplayName() {
      return _displayName;
    }

    public int getNumber() {
      return ordinal() + 1;
    }

  }

  private Level _level;
  private SecuritySource _secSource;
  private OrganizationSource _organizationSource;
  private boolean _includeEmptyCategories;

  public GICSAggregationFunction(SecuritySource secSource, OrganizationSource organizationSource, String level) {
    this(secSource, organizationSource, Enum.valueOf(Level.class, level));
  }

  public GICSAggregationFunction(SecuritySource secSource, OrganizationSource organizationSource, Level level) {
    this(secSource, organizationSource, level, false);
  }

  public GICSAggregationFunction(SecuritySource secSource,
                                 OrganizationSource organizationSource,
                                 Level level,
                                 boolean useAttributes) {
    this(secSource, organizationSource, level, useAttributes, true);
  }

  public GICSAggregationFunction(SecuritySource secSource,
                                 OrganizationSource organizationSource, Level level,
                                 boolean useAttributes,
                                 boolean includeEmptyCategories) {
    _secSource = secSource;
    _organizationSource = organizationSource;
    _level = level;
    _useAttributes = useAttributes;
    _includeEmptyCategories = includeEmptyCategories;
  }

  private FinancialSecurityVisitor<String> _equitySecurityVisitor = new FinancialSecurityVisitorAdapter<String>() {
    @Override
    public String visitEquitySecurity(EquitySecurity security) {
      if (security.getGicsCode() != null) {
        switch (_level) {
          case SECTOR:
            return security.getGicsCode().getSectorDescription();
          case INDUSTRY_GROUP:
            return security.getGicsCode().getIndustryGroupDescription();
          case INDUSTRY:
            return security.getGicsCode().getIndustryDescription();
          case SUB_INDUSTRY:
            return security.getGicsCode().getSubIndustryDescription();
        }
      }
      return UNKNOWN;
    }
  };

  private FinancialSecurityVisitor<String> _equityOptionSecurityVisitor = new FinancialSecurityVisitorAdapter<String>() {
    @Override
    public String visitEquityOptionSecurity(EquityOptionSecurity security) {
      EquitySecurity underlying = (EquitySecurity) _secSource.getSingle(ExternalIdBundle.of(security.getUnderlyingId()));
      if (underlying != null) {
        if (underlying.getGicsCode() != null) {
          switch (_level) {
            case SECTOR:
              return underlying.getGicsCode().getSectorDescription();
            case INDUSTRY_GROUP:
              return underlying.getGicsCode().getIndustryGroupDescription();
            case INDUSTRY:
              return underlying.getGicsCode().getIndustryDescription();
            case SUB_INDUSTRY:
              return underlying.getGicsCode().getSubIndustryDescription();
          }
        }
      }
      return UNKNOWN;
    }
  };
  
  private FinancialSecurityVisitor<String> _equityIndexOptionSecurityVisitor = new FinancialSecurityVisitorAdapter<String>() {
    @Override
    public String visitEquityIndexOptionSecurity(EquityIndexOptionSecurity security) {
      if (_level == Level.SECTOR) {
        return security.getUnderlyingId().getValue();
      } else {
        return UNKNOWN;
      }
    }
  };

  private FinancialSecurityVisitor<String> _standardVanillaCdsSecurityVisitor = new FinancialSecurityVisitorAdapter<String>() {
    @Override
    public String visitStandardVanillaCDSSecurity(StandardVanillaCDSSecurity cds) {
      if (_level == Level.SECTOR) {

        ExternalId refEntityId = cds.getReferenceEntity();
        if (refEntityId.isScheme(ExternalSchemes.MARKIT_RED_CODE)) {

          Organization organization = _organizationSource.getOrganizationByRedCode(refEntityId.getValue());
          return organization.getObligor().getSector().name();
        }
      }
      return UNKNOWN;
    }
  };

  private FinancialSecurityVisitor<String> _legacyVanillaCdsSecurityVisitor = new FinancialSecurityVisitorAdapter<String>() {
    @Override
    public String visitStandardVanillaCDSSecurity(StandardVanillaCDSSecurity cds) {
      if (_level == Level.SECTOR) {

        ExternalId refEntityId = cds.getReferenceEntity();
        if (refEntityId.isScheme(ExternalSchemes.MARKIT_RED_CODE)) {

          Organization organization = _organizationSource.getOrganizationByRedCode(refEntityId.getValue());
          return organization.getObligor().getSector().name();
        }
      }
      return UNKNOWN;
    }
  };

  @Override
  public String classifyPosition(Position position) {
    if (_useAttributes) {
      Map<String, String> attributes = position.getAttributes();
      if (attributes.containsKey(getName())) {
        return attributes.get(getName());
      } else {
        return UNKNOWN;
      }
    } else {
      FinancialSecurityVisitor<String> visitorAdapter = FinancialSecurityVisitorAdapter.<String>builder()
        .equitySecurityVisitor(_equitySecurityVisitor)
        .equityOptionVisitor(_equityOptionSecurityVisitor)
        .equityIndexOptionVisitor(_equityIndexOptionSecurityVisitor)
        .standardVanillaCDSSecurityVisitor(_standardVanillaCdsSecurityVisitor)
        .legacyVanillaCDSSecurityVisitor(_legacyVanillaCdsSecurityVisitor)
        .create();
      FinancialSecurity security = (FinancialSecurity) position.getSecurityLink().resolve(_secSource);
      try {
        String classification = security.accept(visitorAdapter);
        return classification == null ? UNKNOWN : classification;
      } catch (UnsupportedOperationException uoe) {
        return UNKNOWN;
      }
    }
  }

  @Override
  public String getName() {
    return "GICS - level " + _level.getNumber() + " (" + _level.getDisplayName() + ")";
  }

  @Override
  public Collection<String> getRequiredEntries() {
    if (_includeEmptyCategories) {
      Collection<String> baseList = new ArrayList<String>();
      switch (_level) {
        case SECTOR:
          baseList.addAll(GICSCode.getAllSectorDescriptions());
          break;
        case INDUSTRY_GROUP:
          baseList.addAll(GICSCode.getAllIndustryGroupDescriptions());
          break;
        case INDUSTRY:
          baseList.addAll(GICSCode.getAllIndustryDescriptions());
          break;
        case SUB_INDUSTRY:
          baseList.addAll(GICSCode.getAllSubIndustryDescriptions());
          break;
      }
      baseList.add(UNKNOWN);
      return baseList;
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public int compare(String o1, String o2) {
    if (o1.equals(UNKNOWN)) {
      if (o2.equals(UNKNOWN)) {
        return 0;
      }
      return 1;
    } else if (o2.equals(UNKNOWN)) {
      return -1;
    }
    return o1.compareTo(o2);
  }

  @Override
  public Comparator<Position> getPositionComparator() {
    return _comparator;
  }

}
