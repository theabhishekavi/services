package org.opendatakit.sync.service.logic;

import android.test.ApplicationTestCase;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.opendatakit.aggregate.odktables.rest.TableConstants;
import org.opendatakit.aggregate.odktables.rest.entity.ChangeSetList;
import org.opendatakit.aggregate.odktables.rest.entity.Column;
import org.opendatakit.aggregate.odktables.rest.entity.DataKeyValue;
import org.opendatakit.aggregate.odktables.rest.entity.OdkTablesFileManifestEntry;
import org.opendatakit.aggregate.odktables.rest.entity.RowOutcome;
import org.opendatakit.aggregate.odktables.rest.entity.RowOutcomeList;
import org.opendatakit.aggregate.odktables.rest.entity.RowResource;
import org.opendatakit.aggregate.odktables.rest.entity.RowResourceList;
import org.opendatakit.aggregate.odktables.rest.entity.TableDefinitionResource;
import org.opendatakit.aggregate.odktables.rest.entity.TableResource;
import org.opendatakit.aggregate.odktables.rest.entity.TableResourceList;
import org.opendatakit.common.android.application.AppAwareApplication;
import org.opendatakit.common.android.data.ColumnDefinition;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.services.application.Services;
import org.opendatakit.sync.service.SyncExecutionContext;
import org.opendatakit.sync.service.SyncNotification;
import org.opendatakit.sync.service.data.SyncRow;
import org.opendatakit.sync.service.data.SynchronizationResult;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * Created by clarice on 5/6/16.
 */
public class AggregateSynchronizerTest extends ApplicationTestCase<Services> {
  String agg_url;
  String appId;
  String absolutePathOfTestFiles;
  String host;
  String userName;
  String password;
  String appName;
  int batchSize;
  int version;

  public AggregateSynchronizerTest()
  {
    super(Services.class);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    agg_url = "https://test.appspot.com";
    appId = "odktables/default";
    absolutePathOfTestFiles = "testfiles/test/";
    batchSize = 1000;
    userName = "";
    password = "";
    appName = "test";
    URL url = new URL(agg_url);
    host = url.getHost();
    version = 2;
    createApplication();
  }

  /*
   * Perform tear down for tests if necessary
   */
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  /*
   * Test getting the app level file manifest with no files
   */
  public void testGetAppLevelFileManifestWithNoFiles_ExpectPass() {
    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      List<OdkTablesFileManifestEntry> tableManifestEntries = synchronizer.getAppLevelFileManifest(true, null);

      assertEquals(tableManifestEntries.size(), 0);

    } catch (Exception e) {
      TestCase.fail("testGetAppLevelFileManifestWithNoFiles_ExpectPass: expected pass but got exception");
      e.printStackTrace();

    }
  }

  /*
   * Test get list of tables when no tables exist
   */
  public void testGetTablesWhenNoTablesExist_ExpectPass() {
    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      TableResourceList tables = synchronizer.getTables(null);

      assertEquals(tables.getTables().size(), 0);

    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testGetTablesWhenNoTablesExist_ExpectPass: expected pass but got exception");
    }
  }

  /*
   * Test get table definitions when no tables exist
   */
  public void testGetTableDefinitions_ExpectPass() {
    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    String testTableId = "test0";
    String colName = "test_col1";
    String colKey = "test_col1";
    String colType = "string";

    String testTableSchemaETag = "testGetTableDefinitions_ExpectPass";
    String listOfChildElements = "[]";

    ArrayList<Column> columns = new ArrayList<Column>();

    columns.add(new Column(colKey, colName, colType, listOfChildElements));

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);

      assertNotNull(testTableRes);

      assertEquals(testTableRes.getTableId(), testTableId);


      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());

      ArrayList<Column> cols = tableDefRes.getColumns();

      for (int i = 0; i < cols.size(); i++) {
        Column col = cols.get(i);
        assertEquals(col.getElementKey(), colKey);
      }

      assertEquals(tableDefRes.getTableId(), testTableId);

      synchronizer.deleteTable(testTableRes);

    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testGetTableDefinitions_ExpectPass: expected pass but got exception");
    }
  }

  /*
   * Test upload config file
   */
  public void testUploadConfigFileWithTextFile_ExpectPass() {
    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      String fileName = "testFile.txt";

      String destDir = ODKFileUtils.getConfigFolder(appName);

      File destFile = new File(destDir, fileName);

      // Now copy this file over to the correct place on the device
      FileUtils.writeStringToFile(destFile, "This is a test");

      boolean uploadFileStatus = synchronizer.uploadConfigFile(destFile);

      assertTrue(uploadFileStatus);

      boolean deletedFile = synchronizer.deleteConfigFile(destFile);

      assertTrue(deletedFile);

    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testuploadConfigFileWithTextFile_ExpectPass: expected pass but got exception");
    }
  }

  /*
   * Test delete config file
   */
  public void testDeleteConfigFile_ExpectPass() {
    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      String fileName = "testFile.txt";

      String destDir = ODKFileUtils.getConfigFolder(appName);

      File destFile = new File(destDir, fileName);

      // Now copy this file over to the correct place on the device
      FileUtils.writeStringToFile(destFile, "This is a test");

      boolean uploadFileStatus = synchronizer.uploadConfigFile(destFile);

      assertTrue(uploadFileStatus);

      boolean deletedFile = synchronizer.deleteConfigFile(destFile);

      assertTrue(deletedFile);

    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testDeleteConfigFile_ExpectPass: expected pass but got exception");
    }
  }

  /*
   * Test create table with string
   */
  public void testCreateTableWithString_ExpectPass() {

    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    String testTableId = "test0";
    String colName = "test_col1";
    String colKey = "test_col1";
    String colType = "string";

    String testTableSchemaETag = "testCreateTableWithString_ExpectPass";
    String listOfChildElements = "[]";

    ArrayList<Column> columns = new ArrayList<Column>();

    columns.add(new Column(colKey, colName, colType, listOfChildElements));

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);

      assertNotNull(testTableRes);

      assertEquals(testTableRes.getTableId(), testTableId);


      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());

      ArrayList<Column> cols = tableDefRes.getColumns();

      for (int i = 0; i < cols.size(); i++) {
        Column col = cols.get(i);
        assertEquals(col.getElementKey(), colKey);
      }

      synchronizer.deleteTable(testTableRes);

    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testCreateTableWithString_ExpectPass: expected pass but got exception");
    }
  }

  /*
   * Test delete table
   */
  public void testDeleteTable_ExpectPass() {

    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    String testTableId = "test1";
    String colName = "test_col1";
    String colKey = "test_col1";
    String colType = "string";

    String testTableSchemaETag = "testDeleteTable_ExpectPass";
    String listOfChildElements = "[]";

    ArrayList<Column> columns = new ArrayList<Column>();

    columns.add(new Column(colKey, colName, colType, listOfChildElements));

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);

      assertNotNull(testTableRes);

      assertEquals(testTableRes.getTableId(), testTableId);

      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());

      ArrayList<Column> cols = tableDefRes.getColumns();

      for (int i = 0; i < cols.size(); i++) {
        Column col = cols.get(i);
        assertEquals(col.getElementKey(), colKey);
      }

      synchronizer.deleteTable(testTableRes);

    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testDeleteTable_ExpectPass: expected pass but got exception");
    }
  }

  /*
   * Test get change sets
   */
//  public void testGetChangeSetsWhenThereAreNoChanges_ExpectPass() {
//
//    SyncNotification syncProg = new SyncNotification(getContext(), appName);
//    SynchronizationResult syncRes = new SynchronizationResult();
//    AppAwareApplication appAwareContext = getApplication();
//
//    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
//            appName, syncProg, syncRes);
//
//    sharedContext.setODKUsername(userName);
//    sharedContext.setODKPassword(password);
//    sharedContext.setAggregateUri(agg_url);
//
//    String testTableId = "test2";
//    String colName = "test_col1";
//    String colKey = "test_col1";
//    String colType = "string";
//
//    String testTableSchemaETag = "testGetChangeSetsWhenThereAreNoChanges_ExpectPass";
//    String tableDataETag = null;
//    String listOfChildElements = "[]";
//
//    ArrayList<Column> columns = new ArrayList<Column>();
//
//    columns.add(new Column(colKey, colName, colType, listOfChildElements));
//
//    try {
//      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);
//
//      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);
//
//      assertNotNull(testTableRes);
//
//      assertEquals(testTableRes.getTableId(), testTableId);
//
//      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());
//
//      ArrayList<Column> cols = tableDefRes.getColumns();
//
//      for (int i = 0; i < cols.size(); i++) {
//        Column col = cols.get(i);
//        assertEquals(col.getElementKey(), colKey);
//      }
//
//      tableDataETag = testTableRes.getDataETag();
//
//      // Need to get all tables again
//      // Maybe that tableResource will have the right dataETag
//
//      ChangeSetList changeSetList = synchronizer.getChangeSets(testTableRes, tableDataETag);
//
//      ArrayList<String> changeSets = changeSetList.getChangeSets();
//
//      assertEquals(changeSets.size(), 0);
//
//      synchronizer.deleteTable(testTableRes);
//
//    } catch (Exception e) {
//      e.printStackTrace();
//      TestCase.fail("testGetChangeSets_ExpectPass: expected pass but got exception");
//    }
//  }

  /*
   * Test get change set
   */
//  public void testGetChangeSetWhenThereAreNoChanges_ExpectPass() {
//
//    SyncNotification syncProg = new SyncNotification(getContext(), appName);
//    SynchronizationResult syncRes = new SynchronizationResult();
//    AppAwareApplication appAwareContext = getApplication();
//
//    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
//            appName, syncProg, syncRes);
//
//    sharedContext.setODKUsername(userName);
//    sharedContext.setODKPassword(password);
//    sharedContext.setAggregateUri(agg_url);
//
//    String testTableId = "test3";
//    String colName = "test_col1";
//    String colKey = "test_col1";
//    String colType = "string";
//
//    String testTableSchemaETag = "testGetChangeSetWhenThereAreNoChanges_ExpectPass";
//    String tableDataETag = null;
//    String listOfChildElements = "[]";
//
//    ArrayList<Column> columns = new ArrayList<Column>();
//
//    columns.add(new Column(colKey, colName, colType, listOfChildElements));
//
//    try {
//      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);
//
//      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);
//
//      assertNotNull(testTableRes);
//
//      assertEquals(testTableRes.getTableId(), testTableId);
//
//      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());
//
//      ArrayList<Column> cols = tableDefRes.getColumns();
//
//      for (int i = 0; i < cols.size(); i++) {
//        Column col = cols.get(i);
//        assertEquals(col.getElementKey(), colKey);
//      }
//
//      tableDataETag = testTableRes.getDataETag();
//
//      RowResourceList rowResourceList = synchronizer.getChangeSet(testTableRes, tableDataETag, true, null);
//
//      ArrayList<RowResource> rowRes = rowResourceList.getRows();
//
//      assertEquals(rowRes.size(), 0);
//
//      synchronizer.deleteTable(testTableRes);
//
//    } catch (Exception e) {
//      e.printStackTrace();
//      TestCase.fail("testGetChangeSetWhenThereAreNoChanges_ExpectPass: expected pass but got exception");
//    }
//  }

  /*
   * Test get updates when there are no changes
   */
  public void testGetUpdatesWhenThereAreNoChanges_ExpectPass() {

    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    String testTableId = "test4";
    String colName = "test_col1";
    String colKey = "test_col1";
    String colType = "string";

    String testTableSchemaETag = "testGetUpdatesWhenThereAreNoChanges_ExpectPass";
    String tableDataETag = null;
    String listOfChildElements = "[]";

    ArrayList<Column> columns = new ArrayList<Column>();

    columns.add(new Column(colKey, colName, colType, listOfChildElements));

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);

      assertNotNull(testTableRes);

      assertEquals(testTableRes.getTableId(), testTableId);

      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());

      ArrayList<Column> cols = tableDefRes.getColumns();

      for (int i = 0; i < cols.size(); i++) {
        Column col = cols.get(i);
        assertEquals(col.getElementKey(), colKey);
      }

      tableDataETag = testTableRes.getDataETag();

      RowResourceList rowResourceList = synchronizer.getUpdates(testTableRes, tableDataETag, null);

      ArrayList<RowResource> rowRes = rowResourceList.getRows();

      assertEquals(rowRes.size(), 0);

      synchronizer.deleteTable(testTableRes);

    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testGetUpdatesWhenThereAreNoChanges_ExpectPass: expected pass but got exception");
    }
  }


  /*
   * Test get updates when there are changes
   */
  public void testGetUpdatesWhenThereAreUpdates_ExpectPass() {

    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    String testTableId = "test41";
    String colName = "test_col1";
    String colKey = "test_col1";
    String colType = "string";

    String testTableSchemaETag = "testGetUpdatesWhenThereAreUpdates_ExpectPass";
    String tableDataETag = null;
    String listOfChildElements = "[]";

    ArrayList<Column> columns = new ArrayList<Column>();

    columns.add(new Column(colKey, colName, colType, listOfChildElements));

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);

      assertNotNull(testTableRes);

      assertEquals(testTableRes.getTableId(), testTableId);

      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());

      ArrayList<Column> cols = tableDefRes.getColumns();

      for (int i = 0; i < cols.size(); i++) {
        Column col = cols.get(i);
        assertEquals(col.getElementKey(), colKey);
      }

      tableDataETag = testTableRes.getDataETag();

      // Insert a row
      String val = "test value for table " + testTableId;
      DataKeyValue dkv = new DataKeyValue(colKey, val);
      ArrayList<DataKeyValue> dkvl = new ArrayList<DataKeyValue>();
      dkvl.add(dkv);

      ArrayList<SyncRow> listOfRowsToCreate = new ArrayList<SyncRow>();

      ArrayList<ColumnDefinition> colDefs = new ArrayList<ColumnDefinition>();

      String rowId = "uuid:" + UUID.randomUUID().toString();
      String ts = TableConstants.nanoSecondsFromMillis(System.currentTimeMillis());
      SyncRow syncRow = new SyncRow(rowId, null, false, null, null, null, ts, null, null, dkvl, colDefs);

      listOfRowsToCreate.add(syncRow);

      synchronizer.alterRows(testTableRes, listOfRowsToCreate);

      RowResourceList rowResourceList = synchronizer.getUpdates(testTableRes, tableDataETag, null);

      ArrayList<RowResource> rowRes = rowResourceList.getRows();

      assertEquals(rowRes.size(), 1);

      synchronizer.deleteTable(testTableRes);

    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testGetUpdatesWhenThereAreUpdates_ExpectPass: expected pass but got exception");
    }
  }

  /*
   * Test alter rows with an inserted row
   */
  public void testAlterRowsWithInsertedRow_ExpectPass() {
    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    String testTableId = "test5";
    String colName = "test_col1";
    String colKey = "test_col1";
    String colType = "string";

    String RowId = "uuid:" + UUID.randomUUID().toString();

    String utf_val = "Test value";

    String testTableSchemaETag = "testAlterRowsWithInsertedRow_ExpectPass";
    String listOfChildElements = "[]";

    ArrayList<Column> columns = new ArrayList<Column>();

    columns.add(new Column(colKey, colName, colType, listOfChildElements));

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);

      assertNotNull(testTableRes);

      assertEquals(testTableRes.getTableId(), testTableId);

      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());

      ArrayList<Column> cols = tableDefRes.getColumns();

      for (int i = 0; i < cols.size(); i++) {
        Column col = cols.get(i);
        assertEquals(col.getElementKey(), colKey);
      }

      // Create a row of data to attach the batch of files
      DataKeyValue dkv = new DataKeyValue(colKey, utf_val);
      ArrayList<DataKeyValue> dkvl = new ArrayList<DataKeyValue>();
      dkvl.add(dkv);

      ArrayList<SyncRow> listOfRowsToCreate = new ArrayList<SyncRow>();

      ArrayList<ColumnDefinition> colDefs = new ArrayList<ColumnDefinition>();

      String ts = TableConstants.nanoSecondsFromMillis(System.currentTimeMillis());
      SyncRow syncRow = new SyncRow(RowId, null, false, null, null, null, ts, null, null, dkvl, colDefs);

      listOfRowsToCreate.add(syncRow);

      RowOutcomeList rowOutList = synchronizer.alterRows(testTableRes, listOfRowsToCreate);

      ArrayList<RowOutcome> rowOuts = rowOutList.getRows();

      assertEquals(rowOuts.size(), 1);

      synchronizer.deleteTable(testTableRes);
    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testAlterRowsWithInsertedRow_ExpectPass: expected pass but got exception");
    }
  }

  /*
   * Test getting the table level file manifest with no files
   */
  public void testGetTableLevelFileManifest_ExpectPass() {

    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    String testTableId = "test6";
    String colName = "test_col1";
    String colKey = "test_col1";
    String colType = "string";

    String testTableSchemaETag = "testGetTableLevelFileManifest_ExpectPass";
    String listOfChildElements = "[]";

    ArrayList<Column> columns = new ArrayList<Column>();

    columns.add(new Column(colKey, colName, colType, listOfChildElements));

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);

      assertNotNull(testTableRes);

      assertEquals(testTableRes.getTableId(), testTableId);

      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());

      ArrayList<Column> cols = tableDefRes.getColumns();

      for (int i = 0; i < cols.size(); i++) {
        Column col = cols.get(i);
        assertEquals(col.getElementKey(), colKey);
      }

      List<OdkTablesFileManifestEntry> tableManifest =
              synchronizer.getTableLevelFileManifest(testTableId,
                      testTableRes.getTableLevelManifestETag(), false);

      assertEquals(tableManifest.size(), 0);

      synchronizer.deleteTable(testTableRes);
    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testGetTableLevelFileManifest_ExpectPass: expected pass but got exception");
    }
  }

  /*
   * Test getting the row level file manifest with no files
   */
  public void testGetRowLevelFileManifestWhenThereAreNoFiles_ExpectPass() {

    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    String testTableId = "test7";
    String colName = "test_col1";
    String colKey = "test_col1";
    String colType = "string";

    String testTableSchemaETag = "testGetRowLevelFileManifest_ExpectPass";
    String listOfChildElements = "[]";

    ArrayList<Column> columns = new ArrayList<Column>();

    columns.add(new Column(colKey, colName, colType, listOfChildElements));

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);

      assertNotNull(testTableRes);

      assertEquals(testTableRes.getTableId(), testTableId);

      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());

      ArrayList<Column> cols = tableDefRes.getColumns();

      for (int i = 0; i < cols.size(); i++) {
        Column col = cols.get(i);
        assertEquals(col.getElementKey(), colKey);
      }

      // Insert a row
      String val = "test value for table " + testTableId;
      DataKeyValue dkv = new DataKeyValue(colKey, val);
      ArrayList<DataKeyValue> dkvl = new ArrayList<DataKeyValue>();
      dkvl.add(dkv);

      ArrayList<SyncRow> listOfRowsToCreate = new ArrayList<SyncRow>();

      ArrayList<ColumnDefinition> colDefs = new ArrayList<ColumnDefinition>();

      String rowId = "uuid:" + UUID.randomUUID().toString();
      String ts = TableConstants.nanoSecondsFromMillis(System.currentTimeMillis());
      SyncRow syncRow = new SyncRow(rowId, null, false, null, null, null, ts, null, null, dkvl, colDefs);

      listOfRowsToCreate.add(syncRow);

      synchronizer.alterRows(testTableRes, listOfRowsToCreate);

      List<OdkTablesFileManifestEntry> rowManifest =
        synchronizer.getRowLevelFileManifest(testTableRes.getInstanceFilesUri(), testTableId, rowId);

      assertEquals(rowManifest.size(), 0);

      synchronizer.deleteTable(testTableRes);
    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testGetRowLevelFileManifest_ExpectPass: expected pass but got exception");
    }
  }

  /*
   * Test upload instance file
   */
  public void testUploadInstanceFile_ExpectPass() {

    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    String testTableId = "test8";
    String colName = "test_col1";
    String colKey = "test_col1";
    String colType = "string";

    String testTableSchemaETag = "testUploadInstanceFile_ExpectPass";
    String listOfChildElements = "[]";

    ArrayList<Column> columns = new ArrayList<Column>();

    columns.add(new Column(colKey, colName, colType, listOfChildElements));

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      String fileName = "testFile.txt";

      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);

      assertNotNull(testTableRes);

      assertEquals(testTableRes.getTableId(), testTableId);

      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());

      ArrayList<Column> cols = tableDefRes.getColumns();

      for (int i = 0; i < cols.size(); i++) {
        Column col = cols.get(i);
        assertEquals(col.getElementKey(), colKey);
      }

      // Insert a row
      String val = "test value for table " + testTableId;
      DataKeyValue dkv = new DataKeyValue(colKey, val);
      ArrayList<DataKeyValue> dkvl = new ArrayList<DataKeyValue>();
      dkvl.add(dkv);

      ArrayList<SyncRow> listOfRowsToCreate = new ArrayList<SyncRow>();

      ArrayList<ColumnDefinition> colDefs = new ArrayList<ColumnDefinition>();

      String rowId = "uuid:" + UUID.randomUUID().toString();
      String ts = TableConstants.nanoSecondsFromMillis(System.currentTimeMillis());
      SyncRow syncRow = new SyncRow(rowId, null, false, null, null, null, ts, null, null, dkvl, colDefs);

      listOfRowsToCreate.add(syncRow);

      synchronizer.alterRows(testTableRes, listOfRowsToCreate);

      String destDir = ODKFileUtils.getInstanceFolder(appName, testTableId, rowId);

      File destFile = new File(destDir, fileName);

      FileUtils.writeStringToFile(destFile, "This is a test");

      AggregateSynchronizer.CommonFileAttachmentTerms cat1 = synchronizer.computeCommonFileAttachmentTerms(testTableRes.getInstanceFilesUri(),
              testTableId, rowId, fileName);
      boolean uploadSuccessful = synchronizer.uploadInstanceFile(destFile, cat1.instanceFileDownloadUri);

      assertTrue(uploadSuccessful);

      synchronizer.deleteTable(testTableRes);
    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testUploadInstanceFile_ExpectPass: expected pass but got exception");
    }
  }

  /*
   * Test downloading file
   */
  public void testDownloadFile_ExpectPass() {

    SyncNotification syncProg = new SyncNotification(getContext(), appName);
    SynchronizationResult syncRes = new SynchronizationResult();
    AppAwareApplication appAwareContext = getApplication();

    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
            appName, syncProg, syncRes);

    sharedContext.setODKUsername(userName);
    sharedContext.setODKPassword(password);
    sharedContext.setAggregateUri(agg_url);

    String testTableId = "test9";
    String colName = "test_col1";
    String colKey = "test_col1";
    String colType = "string";

    String testTableSchemaETag = "testUploadInstanceFile_ExpectPass";
    String listOfChildElements = "[]";

    ArrayList<Column> columns = new ArrayList<Column>();

    columns.add(new Column(colKey, colName, colType, listOfChildElements));

    try {
      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);

      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);

      assertNotNull(testTableRes);

      assertEquals(testTableRes.getTableId(), testTableId);

      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());

      ArrayList<Column> cols = tableDefRes.getColumns();

      for (int i = 0; i < cols.size(); i++) {
        Column col = cols.get(i);
        assertEquals(col.getElementKey(), colKey);
      }

      // Insert a row
      String val = "test value for table " + testTableId;
      DataKeyValue dkv = new DataKeyValue(colKey, val);
      ArrayList<DataKeyValue> dkvl = new ArrayList<DataKeyValue>();
      dkvl.add(dkv);

      ArrayList<SyncRow> listOfRowsToCreate = new ArrayList<SyncRow>();

      ArrayList<ColumnDefinition> colDefs = new ArrayList<ColumnDefinition>();

      String rowId = "uuid:" + UUID.randomUUID().toString();
      String ts = TableConstants.nanoSecondsFromMillis(System.currentTimeMillis());
      SyncRow syncRow = new SyncRow(rowId, null, false, null, null, null, ts, null, null, dkvl, colDefs);

      listOfRowsToCreate.add(syncRow);

      synchronizer.alterRows(testTableRes, listOfRowsToCreate);

      String fileName = "testFile.txt";

      String destDir = ODKFileUtils.getInstanceFolder(appName, testTableId, rowId);

      File destFile = new File(destDir, fileName);

      FileUtils.writeStringToFile(destFile, "This is a test");

      AggregateSynchronizer.CommonFileAttachmentTerms cat1 = synchronizer.computeCommonFileAttachmentTerms(testTableRes.getInstanceFilesUri(),
              testTableId, rowId, fileName);
      boolean uploadSuccessful = synchronizer.uploadInstanceFile(destFile, cat1.instanceFileDownloadUri);

      assertTrue(uploadSuccessful);

      String fileName2 = "testFile2.txt";

      String destDir2 = ODKFileUtils.getInstanceFolder(appName, testTableId, rowId);

      File destFile2 = new File(destDir2, fileName2);
      int downloadSuccessful = synchronizer.downloadFile(destFile2, cat1.instanceFileDownloadUri);

      assertEquals(downloadSuccessful, HttpURLConnection.HTTP_OK);

      synchronizer.deleteTable(testTableRes);
    } catch (Exception e) {
      e.printStackTrace();
      TestCase.fail("testDownloadFile_ExpectPass: expected pass but got exception");
    }
  }

  /*
   * Test upload batch
   */
//  public void testUploadBatch_ExpectPass() {
//
//    SyncNotification syncProg = new SyncNotification(getContext(), appName);
//    SynchronizationResult syncRes = new SynchronizationResult();
//    AppAwareApplication appAwareContext = getApplication();
//
//    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
//            appName, syncProg, syncRes);
//
//    sharedContext.setODKUsername(userName);
//    sharedContext.setODKPassword(password);
//    sharedContext.setAggregateUri(agg_url);
//
//    String testTableId = "test10";
//    String colName = "test_col1";
//    String colKey = "test_col1";
//    String colType = "string";
//
//    String RowId = "uuid:" + UUID.randomUUID().toString();
//
//    String utf_val = "तुरंत अस्पताल रेफर करें व रास्ते मैं शिशु को ओ. आर. एस देतेरहें";
//
//    String testTableSchemaETag = "testUploadInstanceFile_ExpectPass";
//    String listOfChildElements = "[]";
//
//    ArrayList<Column> columns = new ArrayList<Column>();
//
//    columns.add(new Column(colKey, colName, colType, listOfChildElements));
//
//    try {
//      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);
//
//      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);
//
//      assertNotNull(testTableRes);
//
//      assertEquals(testTableRes.getTableId(), testTableId);
//
//      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());
//
//      ArrayList<Column> cols = tableDefRes.getColumns();
//
//      for (int i = 0; i < cols.size(); i++) {
//        Column col = cols.get(i);
//        assertEquals(col.getElementKey(), colKey);
//      }
//
//      // Create a row of data to attach the batch of files
//      DataKeyValue dkv = new DataKeyValue(colKey, utf_val);
//      ArrayList<DataKeyValue> dkvl = new ArrayList<DataKeyValue>();
//      dkvl.add(dkv);
//
//      ArrayList<SyncRow> listOfRowsToCreate = new ArrayList<SyncRow>();
//
//      ArrayList<ColumnDefinition> colDefs = new ArrayList<ColumnDefinition>();
//
//      String ts = TableConstants.nanoSecondsFromMillis(System.currentTimeMillis());
//      SyncRow syncRow = new SyncRow(RowId, null, false, null, null, null, ts, null, null, dkvl, colDefs);
//
//      listOfRowsToCreate.add(syncRow);
//
//      synchronizer.alterRows(testTableRes, listOfRowsToCreate);
//
//      ArrayList<AggregateSynchronizer.CommonFileAttachmentTerms> listOfCats =
//              new ArrayList<AggregateSynchronizer.CommonFileAttachmentTerms>();
//
//      // Create two test files
//      String fileName = "testFile.txt";
//      String destDir = ODKFileUtils.getInstanceFolder(appName, testTableId, RowId);
//      File destFile = new File(destDir, fileName);
//      FileUtils.writeStringToFile(destFile, "This is a test");
//      AggregateSynchronizer.CommonFileAttachmentTerms cat1 = synchronizer.computeCommonFileAttachmentTerms(testTableRes.getInstanceFilesUri(),
//              testTableId, RowId, fileName);
//      listOfCats.add(cat1);
//
//
//      String fileName2 = "testFile2.txt";
//      String destDir2 = ODKFileUtils.getInstanceFolder(appName, testTableId, RowId);
//      File destFile2 = new File(destDir2, fileName2);
//      FileUtils.writeStringToFile(destFile2, "This is a test 2");
//      AggregateSynchronizer.CommonFileAttachmentTerms cat2 = synchronizer.computeCommonFileAttachmentTerms(testTableRes.getInstanceFilesUri(),
//              testTableId, RowId, fileName2);
//      listOfCats.add(cat2);
//
//      boolean batchUploadedSuccessfully = synchronizer.uploadBatch(listOfCats, testTableRes.getInstanceFilesUri(), RowId, testTableId);
//
//      assertTrue(batchUploadedSuccessfully);
//
//      synchronizer.deleteTable(testTableRes);
//    } catch (Exception e) {
//      e.printStackTrace();
//      TestCase.fail("testDownloadFile_ExpectPass: expected pass but got exception");
//    }
//  }

  /*
   * Test batch downloading of files
   */
//  public void testDownloadBatch_ExpectPass() {
//
//    SyncNotification syncProg = new SyncNotification(getContext(), appName);
//    SynchronizationResult syncRes = new SynchronizationResult();
//    AppAwareApplication appAwareContext = getApplication();
//
//    SyncExecutionContext sharedContext = new SyncExecutionContext(appAwareContext,
//            appName, syncProg, syncRes);
//
//    sharedContext.setODKUsername(userName);
//    sharedContext.setODKPassword(password);
//    sharedContext.setAggregateUri(agg_url);
//
//    String testTableId = "test11";
//    String colName = "test_col1";
//    String colKey = "test_col1";
//    String colType = "string";
//
//    String testTableSchemaETag = "testUploadInstanceFile_ExpectPass";
//    String listOfChildElements = "[]";
//
//    ArrayList<Column> columns = new ArrayList<Column>();
//
//    ArrayList<AggregateSynchronizer.CommonFileAttachmentTerms> listOfCats =
//            new ArrayList<AggregateSynchronizer.CommonFileAttachmentTerms>();
//
//    columns.add(new Column(colKey, colName, colType, listOfChildElements));
//
//    try {
//      AggregateSynchronizer synchronizer = new AggregateSynchronizer(sharedContext);
//
//      TableResource testTableRes = synchronizer.createTable(testTableId, testTableSchemaETag, columns);
//
//      assertNotNull(testTableRes);
//
//      assertEquals(testTableRes.getTableId(), testTableId);
//
//      TableDefinitionResource tableDefRes = synchronizer.getTableDefinition(testTableRes.getDefinitionUri());
//
//      ArrayList<Column> cols = tableDefRes.getColumns();
//
//      for (int i = 0; i < cols.size(); i++) {
//        Column col = cols.get(i);
//        assertEquals(col.getElementKey(), colKey);
//      }
//
//      // Insert a row
//      String val = "test value for table " + testTableId;
//      DataKeyValue dkv = new DataKeyValue(colKey, val);
//      ArrayList<DataKeyValue> dkvl = new ArrayList<DataKeyValue>();
//      dkvl.add(dkv);
//
//      ArrayList<SyncRow> listOfRowsToCreate = new ArrayList<SyncRow>();
//
//      ArrayList<ColumnDefinition> colDefs = new ArrayList<ColumnDefinition>();
//
//      String rowId = "uuid:" + UUID.randomUUID().toString();
//      String ts = TableConstants.nanoSecondsFromMillis(System.currentTimeMillis());
//      SyncRow syncRow = new SyncRow(rowId, null, false, null, null, null, ts, null, null, dkvl, colDefs);
//
//      listOfRowsToCreate.add(syncRow);
//
//      synchronizer.alterRows(testTableRes, listOfRowsToCreate);
//
//      String fileName = "testFile.txt";
//      String destDir = ODKFileUtils.getInstanceFolder(appName, testTableId, rowId);
//      File destFile = new File(destDir, fileName);
//      FileUtils.writeStringToFile(destFile, "This is a test");
//
//
//      AggregateSynchronizer.CommonFileAttachmentTerms cat1 = synchronizer.computeCommonFileAttachmentTerms(testTableRes.getInstanceFilesUri(),
//              testTableId, rowId, fileName);
//      listOfCats.add(cat1);
//
//      boolean uploadSuccessful = synchronizer.uploadInstanceFile(destFile, cat1.instanceFileDownloadUri);
//
//      assertTrue(uploadSuccessful);
//
//      String fileName2 = "testFile2.txt";
//      String destDir2 = ODKFileUtils.getInstanceFolder(appName, testTableId, rowId);
//      File destFile2 = new File(destDir2, fileName2);
//      FileUtils.writeStringToFile(destFile2, "This is a test 2");
//      AggregateSynchronizer.CommonFileAttachmentTerms cat2 = synchronizer.computeCommonFileAttachmentTerms(testTableRes.getInstanceFilesUri(),
//              testTableId, rowId, fileName2);
//      listOfCats.add(cat2);
//
//      uploadSuccessful = synchronizer.uploadInstanceFile(destFile2, cat2.instanceFileDownloadUri);
//
//      assertTrue(uploadSuccessful);
//
//      boolean downloadSuccessful = synchronizer.downloadBatch(listOfCats, testTableRes.getInstanceFilesUri(),
//              rowId, testTableId);
//
//      assertTrue(downloadSuccessful);
//
//      synchronizer.deleteTable(testTableRes);
//
//    } catch (Exception e) {
//      e.printStackTrace();
//      TestCase.fail("testDownloadFile_ExpectPass: expected pass but got exception");
//    }
//  }
}