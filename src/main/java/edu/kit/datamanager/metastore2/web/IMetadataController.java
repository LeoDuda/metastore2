/*
 * Copyright 2019 Karlsruhe Institute of Technology.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.kit.datamanager.metastore2.web;

import edu.kit.datamanager.metastore2.domain.MetadataRecord;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.time.Instant;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

/**
 *
 * @author jejkal
 */
@ApiResponses(value = {
  @ApiResponse(code = 401, message = "Unauthorized is returned if authorization in required but was not provided."),
  @ApiResponse(code = 403, message = "Forbidden is returned if the caller has no sufficient privileges.")})
public interface IMetadataController{

  @ApiOperation(value = "Create a new metadata record.", notes = "This endpoint allows to create a new metadata record by providing the record metadata as JSON document as well as the actual metadata as file upload. The record metadata mainly contains "
          + "the resource identifier the record is associated with as well as the identifier of the schema which can be used to validate the provided metadata document. In the current version, both parameters are required. For future versions, e.g. the metadata "
          + "document might be provided by reference.")
  @RequestMapping(path = "/", method = RequestMethod.POST)
  @ApiResponses(value = {
    @ApiResponse(code = 201, message = "Created is returned only if the record has been validated, persisted and the document was successfully validated and stored.", response = MetadataRecord.class),
    @ApiResponse(code = 400, message = "Bad Request is returned if the provided metadata record is invalid or if the validation using the provided schema failed."),
    @ApiResponse(code = 404, message = "Not found is returned, if no schema for the provided schema id was found."),
    @ApiResponse(code = 409, message = "A Conflict is returned, if there is already a record for the related resource id and the provided schema id.")})
  @ResponseBody
  public ResponseEntity<MetadataRecord> createRecord(
          @ApiParam(value = "Json representation of the metadata record.", required = true) @RequestPart(name = "record", required = true) final MetadataRecord record,
          @ApiParam(value = "The metadata document associated with the record. The document must match the schema selected by the record.", required = true) @RequestPart(name = "document", required = true) final MultipartFile document,
          final WebRequest request,
          final HttpServletResponse response,
          final UriComponentsBuilder uriBuilder);

  @ApiOperation(value = "Get a metadata record by its id.", notes = "Obtain is single record by its identifier. The identifier can be either the numeric identifier or the related resource's identifier. "
          + "Depending on a user's role, accessing a specific record may be allowed or forbidden. Furthermore, a specific version of the record can be returned "
          + "by providing a version number as request parameter.")
  @RequestMapping(value = {"/{id}"}, method = {RequestMethod.GET}, produces = {"application/vnd.datamanager.metadata-record+json"})
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "OK and the record is returned if the record exists and the user has sufficient permission.", response = MetadataRecord.class),
    @ApiResponse(code = 404, message = "Not found is returned, if no record for the provided id or version was found.")})
  @ResponseBody
  public ResponseEntity<MetadataRecord> getRecordById(@ApiParam(value = "The record identifier or related resource identifier.", required = true) @PathVariable(value = "id") String id,
          @ApiParam(value = "The version of the record. This parameter only has an effect if versioning  is enabled.", required = false) @RequestParam(value = "version") Long version,
          WebRequest wr,
          HttpServletResponse hsr);

  @ApiOperation(value = "Get a metadata document by its record's id.", notes = "Obtain is single metadata document identified by its identifier. The identifier can be either the numeric identifier or the related resource's identifier. "
          + "Depending on a user's role, accessing a specific record may be allowed or forbidden. "
          + "Furthermore, a specific version of the metadata document can be returned by providing a version number as request parameter.")
  @RequestMapping(value = {"/{id}"}, method = {RequestMethod.GET})
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "OK and the metadata document is returned if the record exists and the user has sufficient permission."),
    @ApiResponse(code = 404, message = "Not found is returned, if no record for the provided id or version was found.")})
  @ResponseBody
  public ResponseEntity getMetadataDocumentById(@ApiParam(value = "The record identifier or related resource identifier.", required = true) @PathVariable(value = "id") String id,
          @ApiParam(value = "The version of the record. This parameter only has an effect if versioning  is enabled.", required = false) @RequestParam(value = "version") Long version,
          WebRequest wr,
          HttpServletResponse hsr);

  @ApiOperation(value = "Get all records.", notes = "List all records in a paginated and/or sorted form. The result can be refined by providing specific related resource id(s) and/or metadata schema id(s) valid records must match. "
          + "If both parameters are provided, a record matches if its related resource identifier AND the used metadata schema are matching. "
          + "Furthermore, the UTC time of the last update can be provided in three different fashions: 1) Providing only updateFrom returns all records updated at or after the provided date, 2) Providing only updateUntil returns all records updated before or "
          + "at the provided date, 3) Providing both returns all records updated within the provided date range."
          + "If no parameters are provided, all accessible records are listed. If versioning is enabled, only the most recent version is listed.")
  @ApiImplicitParams(value = {
    @ApiImplicitParam(name = "page", dataType = "integer", paramType = "query", value = "Results page you want to retrieve (0..N)"),
    @ApiImplicitParam(name = "size", dataType = "integer", paramType = "query", value = "Number of records per page."),
    @ApiImplicitParam(name = "sort", allowMultiple = true, dataType = "string", paramType = "query", value = "Sorting criteria in the format: property(,asc|desc). Default sort order is ascending. Multiple sort criteria are supported.")})
  @RequestMapping(value = {"/"}, method = {RequestMethod.GET})
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "OK and a list of records or an empty list of no record matches.")})
  @ResponseBody
  public ResponseEntity<List<MetadataRecord>> getRecords(
          @ApiParam(value = "A list of related resource identifiers.", required = false) @RequestParam(value = "resoureId") List<String> relatedIds,
          @ApiParam(value = "A list of metadata schema identifiers.", required = false) @RequestParam(value = "schemaId") List<String> schemaIds,
          @ApiParam(value = "The UTC time of the earliest update of a returned record.", required = false) @RequestParam(name = "from", required = false) Instant updateFrom,
          @ApiParam(value = "The UTC time of the latest update of a returned record.", required = false) @RequestParam(name = "until", required = false) Instant updateUntil,
          Pageable pgbl,
          WebRequest wr,
          HttpServletResponse hsr,
          UriComponentsBuilder ucb);

  @ApiOperation(value = "Update a metadata record.", notes = "Apply an update to the metadata record with the provided identifier and/or its accociated metadata document. The identifier can be either the numeric identifier or the related resource's identifier."
          + "If versioning is enabled, a new version of the record is created. Otherwise, the record and/or its metadata are overwritten.")
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "OK is returned in case of a successful update, e.g. the record (if provided) was in the correct format and the document (if provided) matches the provided schema id. The updated record is returned in the response.", response = MetadataRecord.class),
    @ApiResponse(code = 400, message = "Bad Request is returned if the provided metadata record is invalid or if the validation using the provided schema failed."),
    @ApiResponse(code = 404, message = "Not Found is returned if no record for the provided id or no schema for the provided schema id was found.")})
  @RequestMapping(value = "/{id}", method = RequestMethod.PUT, produces = {"application/json"})
  ResponseEntity<MetadataRecord> updateRecord(
          @ApiParam(value = "The record identifier of related resource identifier.", required = true) @PathVariable("id") String id,
          @ApiParam(value = "JSON representation of the metadata record.", required = false) @RequestPart(name = "record", required = false) final MetadataRecord record,
          @ApiParam(value = "The metadata document associated with the record. The document must match the schema defined in the record.", required = false) @RequestPart(name = "document", required = false) final MultipartFile document,
          final WebRequest request,
          final HttpServletResponse response,
          final UriComponentsBuilder uriBuilder
  );

  @ApiOperation(value = "Delete a record.", notes = "Delete a single metadata record and the associated metadata document. The identifier can be either the numeric identifier or the related resource's identifier. "
          + "Deleting a record typically requires the caller to have special permissions. "
          + "In some cases, deleting a record can also be available for the owner or other privileged users or can be forbidden at all. Deletion of a record affects all versions of the particular record.")
  @RequestMapping(value = {"/{id}"}, method = {RequestMethod.DELETE})
  @ApiResponses(value = {
    @ApiResponse(code = 204, message = "No Content is returned as long as no error occurs while deleting a record. Multiple delete operations to the same record will also return HTTP 204 even if the deletion succeeded in the first call.")})
  @ResponseBody
  public ResponseEntity deleteRecord(@ApiParam(value = "The record identifier or related resource identifier.", required = true) @PathVariable(value = "id") String id, WebRequest wr, HttpServletResponse hsr);
}
