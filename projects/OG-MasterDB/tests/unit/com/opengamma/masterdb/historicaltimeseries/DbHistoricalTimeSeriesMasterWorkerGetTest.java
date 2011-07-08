/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.masterdb.historicaltimeseries;

import static org.testng.AssertJUnit.assertEquals;

import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import com.opengamma.DataNotFoundException;
import com.opengamma.id.ObjectIdentifier;
import com.opengamma.id.UniqueIdentifier;
import com.opengamma.id.VersionCorrection;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesInfoDocument;
import com.opengamma.util.test.DBTest;

/**
 * Tests DbHistoricalTimeSeriesMaster.
 */
public class DbHistoricalTimeSeriesMasterWorkerGetTest extends AbstractDbHistoricalTimeSeriesMasterWorkerTest {
  // superclass sets up dummy database

  private static final Logger s_logger = LoggerFactory.getLogger(DbHistoricalTimeSeriesMasterWorkerGetTest.class);

  @Factory(dataProvider = "databases", dataProviderClass = DBTest.class)
  public DbHistoricalTimeSeriesMasterWorkerGetTest(String databaseType, String databaseVersion) {
    super(databaseType, databaseVersion);
    s_logger.info("running testcases for {}", databaseType);
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  //-------------------------------------------------------------------------
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void test_get_nullUID() {
    _htsMaster.get((UniqueIdentifier) null);
  }

  @Test(expectedExceptions = DataNotFoundException.class)
  public void test_get_versioned_notFoundId() {
    UniqueIdentifier uid = UniqueIdentifier.of("DbHts", "0", "0");
    _htsMaster.get(uid);
  }

  @Test(expectedExceptions = DataNotFoundException.class)
  public void test_get_versioned_notFoundVersion() {
    UniqueIdentifier uid = UniqueIdentifier.of("DbHts", "101", "1");
    _htsMaster.get(uid);
  }

  @Test
  public void test_get_versioned101() {
    UniqueIdentifier uid = UniqueIdentifier.of("DbHts", "101", "0");
    HistoricalTimeSeriesInfoDocument test = _htsMaster.get(uid);
    assert101(test);
  }

  @Test
  public void test_get_versioned102() {
    UniqueIdentifier uid = UniqueIdentifier.of("DbHts", "102", "0");
    HistoricalTimeSeriesInfoDocument test = _htsMaster.get(uid);
    assert102(test);
  }

  //-------------------------------------------------------------------------
  @Test
  public void test_get_versioned_notLatest() {
    UniqueIdentifier uid = UniqueIdentifier.of("DbHts", "201", "0");
    HistoricalTimeSeriesInfoDocument test = _htsMaster.get(uid);
    assert201(test);
  }

  @Test
  public void test_get_versioned_latestVersionNotLatestCorrection() {
    UniqueIdentifier uid = UniqueIdentifier.of("DbHts", "201", "1");
    HistoricalTimeSeriesInfoDocument test = _htsMaster.get(uid);
    assert202(test);
  }

  @Test
  public void test_get_versioned_latest() {
    UniqueIdentifier uid = UniqueIdentifier.of("DbHts", "201", "2");
    HistoricalTimeSeriesInfoDocument test = _htsMaster.get(uid);
    assert203(test);
  }

  //-------------------------------------------------------------------------
  @Test(expectedExceptions = DataNotFoundException.class)
  public void test_get_unversioned_notFound() {
    UniqueIdentifier uid = UniqueIdentifier.of("DbHts", "0");
    _htsMaster.get(uid);
  }

  @Test
  public void test_get_unversioned() {
    UniqueIdentifier uid = UniqueIdentifier.of("DbHts", "201");
    HistoricalTimeSeriesInfoDocument test = _htsMaster.get(uid);
    assert203(test);
  }

  //-------------------------------------------------------------------------
  @Test
  public void test_getObjectIdentifier() {
    ObjectIdentifier oid = ObjectIdentifier.of("DbHts", "201");
    HistoricalTimeSeriesInfoDocument test = _htsMaster.get(oid, VersionCorrection.LATEST);
    assert203(test);
  }

  @Test
  public void test_getObjectIdentifier_earlierCorrection() {
    ObjectIdentifier oid = ObjectIdentifier.of("DbHts", "201");
    HistoricalTimeSeriesInfoDocument test = _htsMaster.get(oid, VersionCorrection.ofCorrectedTo(_version2Instant));
    assert202(test);
  }

  @Test
  public void test_getObjectIdentifier_earlierVersion() {
    ObjectIdentifier oid = ObjectIdentifier.of("DbHts", "201");
    HistoricalTimeSeriesInfoDocument test = _htsMaster.get(oid, VersionCorrection.ofVersionAsOf(_version1Instant));
    assert201(test);
  }

  //-------------------------------------------------------------------------
  @Test
  public void test_toString() {
    assertEquals(_htsMaster.getClass().getSimpleName() + "[DbHts]", _htsMaster.toString());
  }

}
