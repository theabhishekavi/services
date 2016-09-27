/*
 * Copyright (C) 2012 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.services.sync.service.logic;

import com.fasterxml.jackson.core.type.TypeReference;

import org.apache.commons.fileupload.MultipartStream;
import org.opendatakit.aggregate.odktables.rest.entity.*;
import org.opendatakit.exception.ServicesAvailabilityException;
import org.opendatakit.utilities.ODKFileUtils;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.logging.WebLoggerIf;
import org.opendatakit.database.service.DbHandle;
import org.opendatakit.httpclientandroidlib.Header;
import org.opendatakit.httpclientandroidlib.HeaderElement;
import org.opendatakit.httpclientandroidlib.HttpEntity;
import org.opendatakit.httpclientandroidlib.HttpHeaders;
import org.opendatakit.httpclientandroidlib.HttpStatus;
import org.opendatakit.httpclientandroidlib.NameValuePair;
import org.opendatakit.httpclientandroidlib.client.entity.GzipCompressingEntity;
import org.opendatakit.httpclientandroidlib.client.methods.CloseableHttpResponse;
import org.opendatakit.httpclientandroidlib.client.methods.HttpDelete;
import org.opendatakit.httpclientandroidlib.client.methods.HttpGet;
import org.opendatakit.httpclientandroidlib.client.methods.HttpPost;
import org.opendatakit.httpclientandroidlib.client.methods.HttpPut;
import org.opendatakit.httpclientandroidlib.conn.ConnectTimeoutException;
import org.opendatakit.httpclientandroidlib.entity.ContentType;
import org.opendatakit.httpclientandroidlib.entity.StringEntity;
import org.opendatakit.httpclientandroidlib.entity.mime.FormBodyPartBuilder;
import org.opendatakit.httpclientandroidlib.entity.mime.MultipartEntityBuilder;
import org.opendatakit.httpclientandroidlib.entity.mime.content.ByteArrayBody;
import org.opendatakit.httpclientandroidlib.message.BasicNameValuePair;
import org.opendatakit.httpclientandroidlib.util.EntityUtils;
import org.opendatakit.sync.service.SyncAttachmentState;
import org.opendatakit.services.sync.service.SyncExecutionContext;
import org.opendatakit.services.sync.service.data.SyncRow;
import org.opendatakit.services.sync.service.exceptions.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of {@link Synchronizer} for ODK Aggregate.
 *
 * @author the.dylan.price@gmail.com
 * @author sudar.sam@gmail.com
 *
 */
public class AggregateSynchronizer implements Synchronizer {

  private static final String LOGTAG = AggregateSynchronizer.class.getSimpleName();
  public static final int DEFAULT_BOUNDARY_BUFSIZE = 4096;

  /**
   * Maximum number of bytes to put within one bulk upload/download request for
   * row-level instance files.
   */
  public static final long MAX_BATCH_SIZE = 10485760;


  private SyncExecutionContext sc;
  private HttpRestProtocolWrapper wrapper;
  private final Map<String, TableResource> resources;
  private final WebLoggerIf log;

  public AggregateSynchronizer(SyncExecutionContext sc) throws
      InvalidAuthTokenException {
    this.sc = sc;
    this.wrapper = new HttpRestProtocolWrapper(sc);
    this.log = WebLogger.getLogger(sc.getAppName());

    this.resources = new HashMap<String, TableResource>();
  }

  @Override
  public void verifyServerSupportsAppName() throws HttpClientWebException, IOException {

    AppNameList appNameList = null;
    HttpGet request = new HttpGet();
    CloseableHttpResponse response = null;

    URI uri = wrapper.constructListOfAppNamesUri();

    wrapper.buildNoContentJsonResponseRequest(uri, request);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_SC_NOT_FOUND);

      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        throw new BadClientConfigException("server does not implement ODK 2.0 REST api",
                request, response);
      }

      String res = wrapper.convertResponseToString(response);

      appNameList = ODKFileUtils.mapper.readValue(res, AppNameList.class);

      if (!appNameList.contains(sc.getAppName())) {
        throw new ServerDoesNotRecognizeAppNameException("server does not recognize this appName",
                request, response);
      }
    } catch ( NetworkTransmissionException e ) {
      if ( e.getCause() != null && e.getCause() instanceof ConnectTimeoutException ) {
        throw new BadClientConfigException("server did not respond. Is the configuration correct?",
                e.getCause(), request, e.getResponse());
      } else {
        throw e;
      }
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public ArrayList<String> getUserRoles() throws HttpClientWebException, IOException {

    HttpGet request = new HttpGet();
    CloseableHttpResponse response = null;

    URI uri = wrapper.constructListOfUserRolesUri();

    wrapper.buildNoContentJsonResponseRequest(uri, request);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_SC_NOT_FOUND);

      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        // perhaps an older server (pre-v1.4.11) ?
        return new ArrayList<String>();
      }

      String res = wrapper.convertResponseToString(response);
      TypeReference ref = new TypeReference<ArrayList<String>>() { };

      ArrayList<String> rolesList = ODKFileUtils.mapper.readValue(res, ref);

      return rolesList;

    } catch ( NetworkTransmissionException e ) {
      if (e.getCause() != null && e.getCause() instanceof ConnectTimeoutException) {
        throw new BadClientConfigException("server did not respond. Is the configuration correct?",
                e.getCause(), request, e.getResponse());
      } else {
        throw e;
      }
    } catch ( AccessDeniedException e ) {
      // this must be an anonymousUser
      return new ArrayList<String>();
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public   ArrayList<Map<String,Object>>  getUsers() throws HttpClientWebException, IOException {

    HttpGet request = new HttpGet();
    CloseableHttpResponse response = null;

    URI uri = wrapper.constructListOfUsersUri();

    wrapper.buildNoContentJsonResponseRequest(uri, request);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_SC_NOT_FOUND);

      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        // perhaps an older server (pre-v1.4.11) ?
        return new ArrayList<Map<String,Object>>();
      }

      String res = wrapper.convertResponseToString(response);
      TypeReference ref = new TypeReference<ArrayList<Map<String,Object>>>() { };

      ArrayList<Map<String,Object>> rolesList = ODKFileUtils.mapper.readValue(res, ref);

      return rolesList;

    } catch ( NetworkTransmissionException e ) {
      if (e.getCause() != null && e.getCause() instanceof ConnectTimeoutException) {
        throw new BadClientConfigException("server did not respond. Is the configuration correct?",
            e.getCause(), request, e.getResponse());
      } else {
        throw e;
      }
    } catch ( AccessDeniedException e ) {
      // this must be an anonymousUser
      return new ArrayList<Map<String,Object>>();
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public TableResourceList getTables(String webSafeResumeCursor) throws
      HttpClientWebException, IOException {

    TableResourceList tableResources = null;
    HttpGet request = new HttpGet();
    CloseableHttpResponse response = null;

    URI uri = wrapper.constructListOfTablesUri(webSafeResumeCursor);

    wrapper.buildNoContentJsonResponseRequest(uri, request);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_ONLY);

      String res = wrapper.convertResponseToString(response);

      tableResources = ODKFileUtils.mapper.readValue(res, TableResourceList.class);

      return tableResources;
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public TableDefinitionResource getTableDefinition(String tableDefinitionUri)
          throws HttpClientWebException, IOException {

    URI uri = URI.create(tableDefinitionUri).normalize();

    HttpGet request = new HttpGet();
    CloseableHttpResponse response = null;
    TableDefinitionResource definitionRes = null;

    wrapper.buildNoContentJsonResponseRequest(uri, request);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_ONLY);

      String res = wrapper.convertResponseToString(response);

      definitionRes = ODKFileUtils.mapper.readValue(res, TableDefinitionResource.class);

      return definitionRes;
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public TableResource createTable(String tableId, String schemaETag, ArrayList<Column> columns)
      throws HttpClientWebException, IOException {

    // build request
    URI uri = wrapper.constructTableIdUri(tableId);
    TableDefinition definition = new TableDefinition(tableId, schemaETag, columns);
    String tableDefinitionJSON = ODKFileUtils.mapper.writeValueAsString(definition);

    // create table
    TableResource resource;

    CloseableHttpResponse response = null;
    HttpPut request = new HttpPut();
    wrapper.buildJsonContentJsonResponseRequest(uri, request);

    HttpEntity entity = new GzipCompressingEntity(new StringEntity(tableDefinitionJSON, Charset.forName("UTF-8")));
    request.setEntity(entity);

    try {
      // TODO: we also need to put up the key value store/properties.
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_ONLY);

      String res = wrapper.convertResponseToString(response);

      resource = ODKFileUtils.mapper.readValue(res, TableResource.class);
      // save resource
      this.resources.put(resource.getTableId(), resource);
      return resource;
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public void deleteTable(TableResource table) throws HttpClientWebException,
          IOException {
    URI uri = URI.create(table.getDefinitionUri()).normalize();

    HttpDelete request = new HttpDelete();
    CloseableHttpResponse response = null;

    wrapper.buildNoContentJsonResponseRequest(uri, request);

    try {
      // TODO: CAL: response should be used?
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_ONLY);
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }

    this.resources.remove(table.getTableId());
  }

  @Override
  public ChangeSetList getChangeSets(TableResource table, String dataETag) throws HttpClientWebException,
          IOException {

    String tableId = table.getTableId();
    // if we have not yet synced, get the changesets from the beginning of time.
    String effectiveDataETag = (table.getDataETag() != null) ? dataETag : null;
    URI uri = wrapper.constructTableDiffChangeSetsUri(table.getDiffUri(), effectiveDataETag);

    HttpGet request = new HttpGet();
    CloseableHttpResponse response = null;

    wrapper.buildNoContentJsonResponseRequest(uri, request);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_ONLY);
      String res = wrapper.convertResponseToString(response);

      ChangeSetList changeSets = ODKFileUtils.mapper.readValue(res, ChangeSetList.class);

      return changeSets;
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  
  @Override
  public RowResourceList getChangeSet(TableResource table, String dataETag, boolean activeOnly, String websafeResumeCursor)
      throws HttpClientWebException, IOException {

    String tableId = table.getTableId();

    if ((table.getDataETag() == null) || dataETag == null) {
      throw new IllegalArgumentException("dataETag cannot be null!");
    }
    URI uri = wrapper.constructTableDiffChangeSetsForDataETagUri(table.getDiffUri(), dataETag,
        activeOnly, websafeResumeCursor);

    HttpGet request = new HttpGet();
    CloseableHttpResponse response = null;
    wrapper.buildNoContentJsonResponseRequest(uri, request);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_ONLY);

      String res = wrapper.convertResponseToString(response);

      RowResourceList rows = ODKFileUtils.mapper.readValue(res, RowResourceList.class);

      return rows;
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public RowResourceList getUpdates(TableResource table, String dataETag,
      String websafeResumeCursor, int fetchLimit)
          throws HttpClientWebException, IOException {

    String tableId = table.getTableId();
    URI uri;

    HttpGet request = new HttpGet();
    CloseableHttpResponse response = null;

    if ((table.getDataETag() == null) || dataETag == null) {
      uri = wrapper.constructTableDataUri(table.getDataUri(), websafeResumeCursor, fetchLimit);
    } else {
      uri = wrapper.constructTableDataDiffUri(table.getDiffUri(), dataETag, websafeResumeCursor, fetchLimit);
    }

    wrapper.buildNoContentJsonResponseRequest(uri, request);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_ONLY);

      String res = wrapper.convertResponseToString(response);

      RowResourceList rows = ODKFileUtils.mapper.readValue(res, RowResourceList.class);

      return rows;
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public RowOutcomeList alterRows(TableResource resource,
      List<SyncRow> rowsToInsertUpdateOrDelete) throws IOException, HttpClientWebException {

    ArrayList<Row> rows = new ArrayList<Row>();
    for (SyncRow rowToAlter : rowsToInsertUpdateOrDelete) {
      Row row = Row.forUpdate(rowToAlter.getRowId(), rowToAlter.getRowETag(),
          rowToAlter.getFormId(), rowToAlter.getLocale(),
          rowToAlter.getSavepointType(), rowToAlter.getSavepointTimestamp(),
          rowToAlter.getSavepointCreator(), rowToAlter.getRowFilterScope(),
          rowToAlter.getValues());
      row.setDeleted(rowToAlter.isDeleted());
      rows.add(row);
    }
    RowList rlist = new RowList(rows, resource.getDataETag());

    HttpPut request = new HttpPut();
    CloseableHttpResponse response = null;

    String rowListJSON = ODKFileUtils.mapper.writeValueAsString(rlist);
    HttpEntity entity = new GzipCompressingEntity(new StringEntity(rowListJSON,
            Charset.forName("UTF-8")));

    URI uri = URI.create(resource.getDataUri());
    wrapper.buildJsonContentJsonResponseRequest(uri, request);
    request.setEntity(entity);

    RowOutcomeList outcomes;

    try {
      // TODO: response can be HttpStatus.SC_CONFLICT to indicate dataETag change
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_ONLY);
      String res = wrapper.convertResponseToString(response);
      outcomes = ODKFileUtils.mapper.readValue(res, RowOutcomeList.class);
      return outcomes;
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public FileManifestDocument getAppLevelFileManifest(boolean pushLocalFiles, String serverReportedAppLevelETag)
      throws HttpClientWebException, IOException {

    URI fileManifestUri = wrapper.constructAppLevelFileManifestUri();
    String eTag = null;
    try {
      eTag = getManifestSyncETag(null);
    } catch (ServicesAvailabilityException e) {
      log.printStackTrace(e);
      log.e(LOGTAG, "database access error (ignoring)");
    }

    HttpGet request = new HttpGet();
    wrapper.buildNoContentJsonResponseRequest(fileManifestUri, request);

    // don't short-circuit manifest if we are pushing local files,
    // as we need to know exactly what is on the server to minimize
    // transmissions of files being pushed up to the server.
    if (!pushLocalFiles && eTag != null) {
      request.addHeader(HttpHeaders.IF_NONE_MATCH, eTag);
      if ( serverReportedAppLevelETag != null && serverReportedAppLevelETag.equals(eTag) ) {
        // no change -- we can skip the request to the server
        return null;
      }
    }

    CloseableHttpResponse response = null;
    List<OdkTablesFileManifestEntry> theList = null;

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_SC_NOT_MODIFIED);

      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
        // signal this by returning null;
        return null;
      }

      // update the manifest ETag record...
      eTag = response.getFirstHeader(HttpHeaders.ETAG).getValue();

      String res = wrapper.convertResponseToString(response);

      // retrieve the manifest...
      OdkTablesFileManifest manifest;

      manifest = ODKFileUtils.mapper.readValue(res, OdkTablesFileManifest.class);

      if (manifest != null) {
        theList = manifest.getFiles();
      }

      if (theList == null) {
        theList = Collections.emptyList();
      }

      // if the server has no configuration for our client version, then we should
      // fail. It is likely that the user wanted to reset the app server to upload
      // a configuration.
      if (!pushLocalFiles && theList.isEmpty()) {
        throw new ClientDetectedMissingConfigForClientVersionException(
                "server has no configuration for this client version", request, response);
      }

      // and return the list of values...
      return new FileManifestDocument(eTag, theList);
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public FileManifestDocument getTableLevelFileManifest(String tableId,
      String serverReportedTableLevelETag,
      boolean pushLocalFiles)
      throws IOException,
      HttpClientWebException {

    URI fileManifestUri = wrapper.constructTableLevelFileManifestUri(tableId);
    String eTag = null;
    try {
      eTag = getManifestSyncETag(tableId);
    } catch (ServicesAvailabilityException e) {
      log.printStackTrace(e);
      log.e(LOGTAG, "database access error (ignoring)");
    }

    HttpGet request = new HttpGet();
    wrapper.buildNoContentJsonResponseRequest(fileManifestUri, request);
    CloseableHttpResponse response = null;

    // don't short-circuit manifest if we are pushing local files,
    // as we need to know exactly what is on the server to minimize
    // transmissions of files being pushed up to the server.
    if (!pushLocalFiles && eTag != null) {
      request.addHeader(HttpHeaders.IF_NONE_MATCH, eTag);
      if ( serverReportedTableLevelETag != null && serverReportedTableLevelETag.equals(eTag) ) {
        // no change -- we can skip the request to the server
        return null;
      }
    }

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_SC_NOT_MODIFIED);

      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
        // signal this by returning null;
        return null;
      }

      // retrieve the manifest...
      List<OdkTablesFileManifestEntry> theList = null;

      // update the manifest ETag record...
      Header eTagHdr = response.getFirstHeader(HttpHeaders.ETAG);
      eTag = eTagHdr.getValue();

      String res = wrapper.convertResponseToString(response);
      OdkTablesFileManifest manifest = ODKFileUtils.mapper.readValue(res, OdkTablesFileManifest.class);

      if (manifest != null) {
        theList = manifest.getFiles();
      }
      if (theList == null) {
        theList = Collections.emptyList();
      }

      // if the server has no configuration for our client version, then we should
      // fail. It is likely that the user wanted to reset the app server to upload
      // a configuration.
      if (!pushLocalFiles && theList.isEmpty()) {
        throw new ClientDetectedMissingConfigForClientVersionException(
                "server has no configuration for table at this client version", request, response);
      }

      // and return the list of values...
      return new FileManifestDocument(eTag, theList);
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public FileManifestDocument getRowLevelFileManifest(String serverInstanceFileUri,
      String tableId, String instanceId, SyncAttachmentState attachmentState,
      String uriFragmentHash)
      throws HttpClientWebException, IOException {

    URI instanceFileManifestUri =
        wrapper.constructInstanceFileManifestUri(serverInstanceFileUri, instanceId);

    String eTag = null;
    try {
      eTag = getRowLevelManifestSyncETag(serverInstanceFileUri, tableId, instanceId,
          attachmentState, uriFragmentHash);
    } catch (ServicesAvailabilityException e) {
      log.printStackTrace(e);
      log.e(LOGTAG, "database access error (ignoring)");
    }

    HttpGet request = new HttpGet();
    CloseableHttpResponse response = null;
    wrapper.buildNoContentJsonResponseRequest(instanceFileManifestUri, request);

    if ( eTag != null ) {
      request.addHeader(HttpHeaders.IF_NONE_MATCH, eTag);
    }

    List<OdkTablesFileManifestEntry> theList = null;

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_SC_NOT_MODIFIED);

      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
        // signal this by returning null;
        return null;
      }

      // update the manifest ETag record...
      Header eTagHdr = response.getFirstHeader(HttpHeaders.ETAG);
      eTag = eTagHdr.getValue();

      // retrieve the manifest...
      String res = wrapper.convertResponseToString(response);
      OdkTablesFileManifest manifest = ODKFileUtils.mapper.readValue(res, OdkTablesFileManifest.class);

      if (manifest != null) {
        theList = manifest.getFiles();
      }

      if (theList == null) {
        theList = Collections.emptyList();
      }
      log.i(LOGTAG, "returning a row-level manifest for " + instanceId);

      // and return the list of values...
      return new FileManifestDocument(eTag, theList);
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  /**
   * Download the file at the given URI to the specified local file.
   *
   * @param destFile
   * @param downloadUrl
   * @throws HttpClientWebException
   * @throws IOException
   */
  @Override
  public void downloadFile(File destFile, URI downloadUrl) throws HttpClientWebException,
      IOException {

    // WiFi network connections can be renegotiated during a large form download
    // sequence.
    // This will cause intermittent download failures. Silently retry once after
    // each
    // failure. Only if there are two consecutive failures, do we abort.
    boolean success = false;
    int attemptCount = 0;
    while (!success && attemptCount++ <= 2) {

      HttpGet request = new HttpGet();
      // no body content-type and no response content-type requested
      wrapper.buildBasicRequest(downloadUrl, request);
      if ( destFile.exists() ) {
        String md5Hash = ODKFileUtils.getMd5Hash(sc.getAppName(), destFile);
        request.addHeader(HttpHeaders.IF_NONE_MATCH, md5Hash);
      }

      CloseableHttpResponse response = null;
      try {
        response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_SC_NOT_MODIFIED);
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
          log.i(LOGTAG, "downloading " + downloadUrl.toString() + " returns non-modified -- No-Op");
          return;
        }

        File tmp = new File(destFile.getParentFile(), destFile.getName() + ".tmp");
        int totalLen = 0;
        InputStream is = null;
        BufferedOutputStream os = null;
        try {
          // open the InputStream of the (uncompressed) entity body...
          is = response.getEntity().getContent();

          os = new BufferedOutputStream(new FileOutputStream(tmp));

          // write connection to temporary file
          byte buf[] = new byte[8192];
          int len;
          while ((len = is.read(buf, 0, buf.length)) >= 0) {
            if (len != 0) {
              totalLen += len;
              os.write(buf, 0, len);
            }
          }
          is.close();
          is = null;

          os.flush();
          os.close();
          os = null;

          success = tmp.renameTo(destFile);
        } catch (Exception e) {
          // most likely a socket timeout
          e.printStackTrace();
          log.e(LOGTAG,  "downloading " + downloadUrl.toString() + " failed after " + totalLen + " bytes: " + e.toString());
          try {
            // signal to the framework that this socket is hosed.
            // with the various nested streams, this may not work...
            is.reset();
          } catch ( Exception ex ) {
            // ignore
          }
          throw e;
        } finally {
          if (os != null) {
            try {
              os.close();
            } catch (Exception e) {
              // no-op
            }
          }
          if (is != null) {
            try {
              // ensure stream is consumed...
              byte buf[] = new byte[8192];
              while (is.read(buf) >= 0)
                ;
            } catch (Exception e) {
              // no-op
            }
            try {
              is.close();
            } catch (Exception e) {
              // no-op
            }
          }
          if (tmp.exists()) {
            tmp.delete();
          }

          if (response != null) {
            response.close();
          }
        }
      } catch (Exception e) {
        log.printStackTrace(e);
        if (attemptCount != 1) {
          throw e;
        }
      } finally {
        if ( response != null ) {
          EntityUtils.consumeQuietly(response.getEntity());
          response.close();
        }
      }
    }
  }

  @Override
  public void deleteConfigFile(File localFile) throws HttpClientWebException, IOException {
    String pathRelativeToConfigFolder = ODKFileUtils.asConfigRelativePath(sc.getAppName(),
        localFile);
    URI filesUri = wrapper.constructConfigFileUri(pathRelativeToConfigFolder);
    log.i(LOGTAG, "CLARICE:[deleteConfigFile] fileDeleteUri: " + filesUri.toString());

    HttpDelete request = new HttpDelete();
    CloseableHttpResponse response = null;
    wrapper.buildNoContentJsonResponseRequest(filesUri, request);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_ONLY);
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public void uploadConfigFile(File localFile) throws HttpClientWebException, IOException {
    String pathRelativeToConfigFolder = ODKFileUtils.asConfigRelativePath(sc.getAppName(),
        localFile);
    URI filesUri = wrapper.constructConfigFileUri(pathRelativeToConfigFolder);
    log.i(LOGTAG, "[uploadConfigFile] filePostUri: " + filesUri.toString());
    String ct = wrapper.determineContentType(localFile.getName());
    ContentType contentType = ContentType.create(ct);

    CloseableHttpResponse response = null;
    HttpPost request = new HttpPost();
    wrapper.buildSpecifiedContentJsonResponseRequest(filesUri, contentType, request);

    HttpEntity entity = wrapper.makeHttpEntity(localFile);
    request.setEntity(entity);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_CREATED_SC_ACCEPTED);
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public void uploadInstanceFile(File file, URI instanceFileUri) throws HttpClientWebException,
      IOException
  {
    log.i(LOGTAG, "[uploadInstanceFile] filePostUri: " + instanceFileUri.toString());
    String ct = wrapper.determineContentType(file.getName());
    ContentType contentType = ContentType.create(ct);

    CloseableHttpResponse response = null;
    HttpPost request = new HttpPost();
    wrapper.buildSpecifiedContentJsonResponseRequest(instanceFileUri, contentType, request);

    HttpEntity entity = wrapper.makeHttpEntity(file);
    request.setEntity(entity);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_CREATED);
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public CommonFileAttachmentTerms createCommonFileAttachmentTerms(String serverInstanceFileUri,
      String tableId, String instanceId, String rowpathUri) {

    File localFile =
        ODKFileUtils.getRowpathFile(sc.getAppName(), tableId, instanceId, rowpathUri);

    // use a cleaned-up rowpathUri in case there are leading slashes, instance paths, etc.
    String cleanRowpathUri = ODKFileUtils.asRowpathUri(sc.getAppName(), tableId, instanceId, localFile);

    URI instanceFileDownloadUri = wrapper.constructInstanceFileUri(serverInstanceFileUri,
        instanceId, cleanRowpathUri);

    CommonFileAttachmentTerms cat = new CommonFileAttachmentTerms();
    cat.rowPathUri = rowpathUri;
    cat.localFile = localFile;
    cat.instanceFileDownloadUri = instanceFileDownloadUri;

    return cat;
  }

  @Override
  public void uploadInstanceFileBatch(List<CommonFileAttachmentTerms> batch,
      String serverInstanceFileUri, String instanceId, String tableId) throws HttpClientWebException, IOException {

    URI instanceFilesUploadUri = wrapper.constructInstanceFileBulkUploadUri(serverInstanceFileUri, instanceId);
    String boundary = "ref" + UUID.randomUUID();

    NameValuePair params = new BasicNameValuePair("boundary", boundary);
    ContentType mt = ContentType.create(ContentType.MULTIPART_FORM_DATA.getMimeType(), params);

    HttpPost request = new HttpPost();
    CloseableHttpResponse response = null;
    wrapper.buildSpecifiedContentJsonResponseRequest(instanceFilesUploadUri, mt, request);

    MultipartEntityBuilder mpEntBuilder = MultipartEntityBuilder.create();

    mpEntBuilder.setBoundary(boundary);

    for (CommonFileAttachmentTerms cat : batch) {
      log.i(LOGTAG, "[uploadFile] filePostUri: " + cat.instanceFileDownloadUri.toString());
      String ct = wrapper.determineContentType(cat.localFile.getName());

      String filename = ODKFileUtils
          .asRowpathUri(sc.getAppName(), tableId, instanceId, cat.localFile);
      filename = filename.replace("\"", "\"\"");

      FormBodyPartBuilder formPartBodyBld = FormBodyPartBuilder.create();
      formPartBodyBld.addField("Content-Disposition", "file;filename=\"" + filename + "\"");
      formPartBodyBld.addField("Content-Type", ct);

      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      InputStream is = null;
      try {
        is = new BufferedInputStream(new FileInputStream(cat.localFile));
        int length = 1024;
        // Transfer bytes from in to out
        byte[] data = new byte[length];
        int len;
        while ((len = is.read(data, 0, length)) >= 0) {
          if (len != 0) {
            bo.write(data, 0, len);
          }
        }
      } finally {
        is.close();
      }

      byte[] content = bo.toByteArray();

      ByteArrayBody byteArrayBod = new ByteArrayBody(content, filename);
      formPartBodyBld.setBody(byteArrayBod);
      formPartBodyBld.setName(filename);
      mpEntBuilder.addPart(formPartBodyBld.build());
    }

    HttpEntity mpFormEntity = mpEntBuilder.build();
    request.setEntity(mpFormEntity);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_CREATED);
    } finally {
      if ( response != null ) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  @Override
  public void downloadInstanceFileBatch(List<CommonFileAttachmentTerms> filesToDownload,
      String serverInstanceFileUri, String instanceId, String tableId) throws HttpClientWebException, IOException {
    // boolean downloadedAllFiles = true;

    URI instanceFilesDownloadUri = wrapper.constructInstanceFileBulkDownloadUri(serverInstanceFileUri, instanceId);

    ArrayList<OdkTablesFileManifestEntry> entries = new ArrayList<OdkTablesFileManifestEntry>();
    for (CommonFileAttachmentTerms cat : filesToDownload) {
      OdkTablesFileManifestEntry entry = new OdkTablesFileManifestEntry();
      entry.filename = cat.rowPathUri;
      entries.add(entry);
    }

    OdkTablesFileManifest manifest = new OdkTablesFileManifest();
    manifest.setFiles(entries);

    String boundaryVal = null;
    InputStream inStream = null;
    OutputStream os = null;

    HttpPost request = new HttpPost();
    CloseableHttpResponse response = null;

    // no body content-type and no response content-type requested
    wrapper.buildBasicRequest(instanceFilesDownloadUri, request);
    request.addHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());

    String fileManifestEntries = ODKFileUtils.mapper.writeValueAsString(manifest);

    HttpEntity entity = new StringEntity(fileManifestEntries,
        Charset.forName("UTF-8"));

    request.setEntity(entity);

    try {
      response = wrapper.httpClientExecute(request, HttpRestProtocolWrapper.SC_OK_ONLY);

      Header hdr = response.getEntity().getContentType();
      hdr.getElements();
      HeaderElement[] hdrElem = hdr.getElements();
      for (HeaderElement elm : hdrElem) {
        int cnt = elm.getParameterCount();
        for (int i = 0; i < cnt; i++) {
          NameValuePair nvp = elm.getParameter(i);
          String nvp_name = nvp.getName();
          String nvp_value = nvp.getValue();
          if (nvp_name.equals(HttpRestProtocolWrapper.BOUNDARY)) {
            boundaryVal = nvp_value;
            break;
          }
        }
      }

      // Best to return at this point if we can't
      // determine the boundary to parse the multi-part form
      if (boundaryVal == null) {
        throw new ClientDetectedVersionMismatchedServerResponseException(
            "unable to extract boundary parameter", request, response);
      }

      inStream = response.getEntity().getContent();

      byte[] msParam = boundaryVal.getBytes(Charset.forName("UTF-8"));
      MultipartStream multipartStream = new MultipartStream(inStream, msParam, DEFAULT_BOUNDARY_BUFSIZE, null);

      // Parse the request
      boolean nextPart = multipartStream.skipPreamble();
      while (nextPart) {
        String header = multipartStream.readHeaders();
        System.out.println("Headers: " + header);

        String partialPath = wrapper.extractInstanceFileRelativeFilename(header);

        if (partialPath == null) {
          log.e("putAttachments", "Server did not identify the rowPathUri for the file");
          throw new ClientDetectedVersionMismatchedServerResponseException(
              "server did not specify rowPathUri for file", request, response);
        }

        File instFile = ODKFileUtils
            .getRowpathFile(sc.getAppName(), tableId, instanceId, partialPath);

        os = new BufferedOutputStream(new FileOutputStream(instFile));

        multipartStream.readBodyData(os);
        os.flush();
        os.close();

        nextPart = multipartStream.readBoundary();
      }
    } finally {
      if (os != null) {
        try {
          os.close();
        } catch (IOException e) {
          e.printStackTrace();
          System.out.println("batchGetFilesForRow: Download file batches: Error closing output stream");
        }
      }
      if (response != null) {
        EntityUtils.consumeQuietly(response.getEntity());
        response.close();
      }
    }
  }

  /**********************************************************************************
   *
   * Database interactions
   **********************************************************************************/

  @Override
  public void deleteAllSyncETagsExceptForCurrentServer() throws ServicesAvailabilityException {
    DbHandle db = null;
    try {
      db = sc.getDatabase();
      sc.getDatabaseService().deleteAllSyncETagsExceptForServer(sc.getAppName(), db,
              sc.getAggregateUri());
    } finally {
      sc.releaseDatabase(db);
      db = null;
    }
  }

  @Override
  public String getFileSyncETag(URI
      fileDownloadUri, String tableId, long lastModified) throws ServicesAvailabilityException {
    DbHandle db = null;
    try {
      db = sc.getDatabase();
      return sc.getDatabaseService().getFileSyncETag(sc.getAppName(), db,
          fileDownloadUri.toString(), tableId,
          lastModified);
    } finally {
      sc.releaseDatabase(db);
      db = null;
    }
  }

  @Override
  public void updateFileSyncETag(URI fileDownloadUri, String tableId, long lastModified, String documentETag) throws ServicesAvailabilityException {
    DbHandle db = null;
    try {
      db = sc.getDatabase();
      sc.getDatabaseService().updateFileSyncETag(sc.getAppName(), db, fileDownloadUri.toString(), tableId,
          lastModified, documentETag);
    } finally {
      sc.releaseDatabase(db);
      db = null;
    }
  }

  @Override
  public String getManifestSyncETag(String tableId) throws ServicesAvailabilityException {

    URI fileManifestUri;

    if ( tableId == null ) {
      fileManifestUri = wrapper.constructAppLevelFileManifestUri();
    } else {
      fileManifestUri = wrapper.constructTableLevelFileManifestUri(tableId);
    }

    DbHandle db = null;
    try {
      db = sc.getDatabase();
      return sc.getDatabaseService().getManifestSyncETag(sc.getAppName(), db, fileManifestUri.toString(), tableId);
    } finally {
      sc.releaseDatabase(db);
      db = null;
    }
  }

  @Override
  public void updateManifestSyncETag(String tableId, String documentETag) throws ServicesAvailabilityException {

    URI fileManifestUri;

    if ( tableId == null ) {
      fileManifestUri = wrapper.constructAppLevelFileManifestUri();
    } else {
      fileManifestUri = wrapper.constructTableLevelFileManifestUri(tableId);
    }

    DbHandle db = null;
    try {
      db = sc.getDatabase();
      sc.getDatabaseService().updateManifestSyncETag(sc.getAppName(), db, fileManifestUri.toString(), tableId,
          documentETag);
    } finally {
      sc.releaseDatabase(db);
      db = null;
    }
  }

  @Override
  public String getRowLevelManifestSyncETag(String serverInstanceFileUri, String tableId,
      String rowId, SyncAttachmentState attachmentState, String uriFragmentHash) throws
      ServicesAvailabilityException {

    URI fileManifestUri = wrapper.constructInstanceFileManifestUri(serverInstanceFileUri, rowId);

    /**
     * When we are obtaining the manifest from the server, we need to:
     *
     * (1) If the current attachmentState does not match the previous fetch's state, we
     * need to pull the server manifest in its entirety.
     * (2) If the list of attachment filenames has changed since the previous fetch, we
     * need to pull the server manifest in its entirety.
     * (3) Otherwise, if we are using the same attachmentState and have the same list of
     * attachment filenames, we can short-circuit the processing if there is no change
     * to the manifest on the server.
     *
     * Accomplish this by prefixing the documentETag with a restrictive prefix and only
     * returning the eTag if that prefix matches.
     */
    DbHandle db = null;
    try {
      db = sc.getDatabase();
      String qualifiedETag = sc.getDatabaseService().getManifestSyncETag(sc.getAppName(), db,
                                  fileManifestUri.toString(), tableId);
      String restrictivePrefix = attachmentState.name() + "." + uriFragmentHash + "|";
      if ( qualifiedETag != null && qualifiedETag.startsWith(restrictivePrefix) ) {
        return qualifiedETag.substring(restrictivePrefix.length());
      } else {
        return null;
      }
    } finally {
      sc.releaseDatabase(db);
      db = null;
    }
  }

  @Override
  public void updateRowLevelManifestSyncETag(String serverInstanceFileUri, String tableId,
      String rowId, SyncAttachmentState attachmentState, String uriFragmentHash,
      String documentETag)
      throws ServicesAvailabilityException {

    URI fileManifestUri = wrapper.constructInstanceFileManifestUri(serverInstanceFileUri, rowId);

    /**
     * When we are obtaining the manifest from the server, we need to:
     *
     * (1) If the current attachmentState does not match the previous fetch's state, we
     * need to pull the server manifest in its entirety.
     * (2) If the list of attachment filenames has changed since the previous fetch, we
     * need to pull the server manifest in its entirety.
     * (3) Otherwise, if we are using the same attachmentState and have the same list of
     * attachment filenames, we can short-circuit the processing if there is no change
     * to the manifest on the server.
     *
     * Accomplish this by prefixing the documentETag with a restrictive prefix and only
     * returning the eTag if that prefix matches.
     */
    DbHandle db = null;
    try {
      db = sc.getDatabase();
      String restrictivePrefix = attachmentState.name() + "." + uriFragmentHash + "|";
      if ( documentETag != null ) {
        documentETag = restrictivePrefix + documentETag;
      }
      sc.getDatabaseService().updateManifestSyncETag(sc.getAppName(), db,
          fileManifestUri.toString(), tableId, documentETag);
    } finally {
      sc.releaseDatabase(db);
      db = null;
    }
  }

  @Override
  public void updateTableSchemaETagAndPurgePotentiallyChangedDocumentETags(String tableId,
      String newSchemaETag, String oldSchemaETag) throws
      ServicesAvailabilityException {
    // we are creating data on the server
    DbHandle db = null;

    try {
      String tableInstanceFilesUriString = null;

      if ( oldSchemaETag != null) {
        URI uri = wrapper.constructRealizedTableIdUri(tableId, oldSchemaETag);
        tableInstanceFilesUriString = uri.toString();
      }

      db = sc.getDatabase();
      sc.getDatabaseService().privilegedServerTableSchemaETagChanged(sc.getAppName(), db,
          tableId, newSchemaETag, tableInstanceFilesUriString);
    } finally {
      sc.releaseDatabase(db);
      db = null;
    }
  }
}
