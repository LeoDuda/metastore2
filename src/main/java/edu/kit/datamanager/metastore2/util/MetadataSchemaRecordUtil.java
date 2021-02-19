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
package edu.kit.datamanager.metastore2.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.datamanager.exceptions.BadArgumentException;
import edu.kit.datamanager.exceptions.CustomInternalServerError;
import edu.kit.datamanager.exceptions.ResourceNotFoundException;
import edu.kit.datamanager.exceptions.UnprocessableEntityException;
import edu.kit.datamanager.metastore2.configuration.MetastoreConfiguration;
import edu.kit.datamanager.metastore2.domain.MetadataSchemaRecord;
import edu.kit.datamanager.metastore2.validation.IValidator;
import edu.kit.datamanager.repo.configuration.RepoBaseConfiguration;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.Date;
import edu.kit.datamanager.repo.domain.PrimaryIdentifier;
import edu.kit.datamanager.repo.domain.ResourceType;
import edu.kit.datamanager.repo.domain.Title;
import edu.kit.datamanager.repo.service.IContentInformationService;
import edu.kit.datamanager.repo.util.ContentDataUtils;
import edu.kit.datamanager.repo.util.DataResourceUtils;
import edu.kit.datamanager.util.ControllerUtils;
import io.swagger.v3.core.util.Json;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * Utility class for handling json documents
 */
public class MetadataSchemaRecordUtil {

  /**
   * Logger for messages.
   */
  private static final Logger LOG = LoggerFactory.getLogger(MetadataSchemaRecordUtil.class);
  /**
   * Encoding for strings/inputstreams.
   */
  private static final String ENCODING = "UTF-8";
  /**
   * Mapper for parsing json.
   */
  private static ObjectMapper mapper = new ObjectMapper();
  private static String guestToken = null;

  public static MetadataSchemaRecord createMetadataSchemaRecord(MetastoreConfiguration applicationProperties,
          MultipartFile recordDocument, MultipartFile document) {
    MetadataSchemaRecord result = null;
    MetadataSchemaRecord record;

    // Do some checks first.
    if (recordDocument == null || recordDocument.isEmpty() || document == null || document.isEmpty()) {
      String message = "No metadata record and/or metadata document provided. Returning HTTP BAD_REQUEST.";
      LOG.error(message);
      throw new BadArgumentException(message);
    }
    try {
      record = Json.mapper().readValue(recordDocument.getInputStream(), MetadataSchemaRecord.class);
    } catch (IOException ex) {
      String message = "No valid metadata record provided. Returning HTTP BAD_REQUEST.";
      LOG.error(message);
      throw new BadArgumentException(message);
    }

    if (record.getSchemaId() == null) {
      String message = "Mandatory attributes schemaId not found in record. Returning HTTP BAD_REQUEST.";
      LOG.error(message);
      throw new BadArgumentException(message);
    } else {
      try {
        String value = URLEncoder.encode(record.getSchemaId(), StandardCharsets.UTF_8.toString());
        if (!value.equals(record.getSchemaId())) {
          String message = "Not a valid schema id! Encoded: " + value;
          LOG.error(message);
          throw new BadArgumentException(message);
        }
      } catch (UnsupportedEncodingException ex) {
        String message = "Error encoding schemaId " + record.getSchemaId();
        LOG.error(message);
        throw new CustomInternalServerError(message);
      }
    }
    // End of parameter checks
    validateMetadataSchemaDocument(applicationProperties, record, document);

    // create record.
    DataResource dataResource = migrateToDataResource(applicationProperties, record);
    DataResource createResource = DataResourceUtils.createResource(applicationProperties, dataResource);
    // store document
    ContentInformation contentInformation = ContentDataUtils.addFile(applicationProperties, createResource, document, document.getOriginalFilename(), null, true, (t) -> {
      return "somethingStupid";
    });

    return migrateToMetadataSchemaRecord(applicationProperties, createResource);
  }

  public static MetadataSchemaRecord updateMetadataSchemaRecord(MetastoreConfiguration applicationProperties,
          String resourceId,
          String eTag,
          MultipartFile recordDocument,
          MultipartFile schemaDocument,
          Function<String, String> supplier) {
    MetadataSchemaRecord record = null;
    MetadataSchemaRecord existingRecord;
    DataResource newResource;

    // Do some checks first.
    if ((recordDocument == null || recordDocument.isEmpty()) && (schemaDocument == null || schemaDocument.isEmpty())) {
      String message = "Neither metadata record nor metadata document provided.";
      LOG.error(message);
      throw new BadArgumentException(message);
    }
    if (!(recordDocument == null || recordDocument.isEmpty())) {
      try {
        record = Json.mapper().readValue(recordDocument.getInputStream(), MetadataSchemaRecord.class);
      } catch (IOException ex) {
        String message = "Can't map record document to MetadataSchemaRecord";
        LOG.error(message);
        throw new BadArgumentException(message);
      }
    }

    LOG.trace("Obtaining most recent metadata record with id {}.", resourceId);
    DataResource dataResource = applicationProperties.getDataResourceService().findById(resourceId);
    LOG.trace("Checking provided ETag.");
    ControllerUtils.checkEtag(eTag, dataResource);
    if (record != null) {
      existingRecord = migrateToMetadataSchemaRecord(applicationProperties, dataResource);
      existingRecord = mergeRecords(existingRecord, record);
      dataResource = migrateToDataResource(applicationProperties, existingRecord);
    }

    if (schemaDocument != null) {
      validateMetadataSchemaDocument(applicationProperties, record, schemaDocument);
      LOG.trace("Updating metadata document.");
      ContentInformation info;
      info = getContentInformationOfResource(applicationProperties, dataResource);

      ContentDataUtils.addFile(applicationProperties, dataResource, schemaDocument, info.getRelativePath(), null, true, supplier);
    }
    dataResource = DataResourceUtils.updateResource(applicationProperties, resourceId, dataResource, eTag, supplier);
    return migrateToMetadataSchemaRecord(applicationProperties, dataResource);
  }

  public static void deleteMetadataSchemaRecord(MetastoreConfiguration applicationProperties,
          String id,
          String eTag,
          Function<String, String> supplier) {
    DataResourceUtils.deleteResource(applicationProperties, id, eTag, supplier);
  }

  public static DataResource migrateToDataResource(RepoBaseConfiguration applicationProperties,
          MetadataSchemaRecord metadataSchemaRecord) {
    DataResource dataResource = null;
    if (metadataSchemaRecord != null) {
      if (metadataSchemaRecord.getSchemaId() != null) {
        try {
          dataResource = applicationProperties.getDataResourceService().findById(metadataSchemaRecord.getSchemaId(), metadataSchemaRecord.getSchemaVersion());
          dataResource = DataResourceUtils.copyDataResource(dataResource);
        } catch (ResourceNotFoundException | NullPointerException rnfe) {
          LOG.error("Error catching DataResource for " + metadataSchemaRecord.getSchemaId() + " -> " + rnfe.getMessage());
          dataResource = DataResource.factoryNewDataResource(metadataSchemaRecord.getSchemaId());
        }
      } else {
        dataResource = new DataResource();
      }
      dataResource.setAcls(metadataSchemaRecord.getAcl());
      if (metadataSchemaRecord.getCreatedAt() != null) {
        boolean createDateExists = false;
        Set<Date> dates = dataResource.getDates();
        for (edu.kit.datamanager.repo.domain.Date d : dates) {
          if (edu.kit.datamanager.repo.domain.Date.DATE_TYPE.CREATED.equals(d.getType())) {
            LOG.trace("Creation date entry found.");
            createDateExists = true;
            break;
          }
        }
        if (!createDateExists) {
          dataResource.getDates().add(Date.factoryDate(metadataSchemaRecord.getCreatedAt(), Date.DATE_TYPE.CREATED));
        }
      }
      if (metadataSchemaRecord.getPid() != null) {
        dataResource.setIdentifier(PrimaryIdentifier.factoryPrimaryIdentifier(metadataSchemaRecord.getPid()));
      }
      String defaultTitle = metadataSchemaRecord.getMimeType();
      boolean titleExists = false;
      for (Title title : dataResource.getTitles()) {
//        if (title.getTitleType() == Title.TYPE.OTHER && title.getValue().equals(defaultTitle)) {
        if (title.getTitleType() == Title.TYPE.OTHER) {
          title.setValue(defaultTitle);
          titleExists = true;
        }
      }
      if (!titleExists) {
        dataResource.getTitles().add(Title.factoryTitle(defaultTitle, Title.TYPE.OTHER));
      }
      dataResource.setResourceType(ResourceType.createResourceType(MetadataSchemaRecord.RESOURCE_TYPE));
      dataResource.getFormats().clear();
      dataResource.getFormats().add(metadataSchemaRecord.getType().name());
    }
    return dataResource;
  }

  public static MetadataSchemaRecord migrateToMetadataSchemaRecord(RepoBaseConfiguration applicationProperties,
          DataResource dataResource) {
    MetadataSchemaRecord metadataSchemaRecord = new MetadataSchemaRecord();
    if (dataResource != null) {
      metadataSchemaRecord.setSchemaId(dataResource.getId());
      MetadataSchemaRecord.SCHEMA_TYPE schemaType = MetadataSchemaRecord.SCHEMA_TYPE.valueOf(dataResource.getFormats().iterator().next());
      metadataSchemaRecord.setType(schemaType);
      metadataSchemaRecord.setMimeType(dataResource.getTitles().iterator().next().getValue());
      metadataSchemaRecord.setETag(dataResource.getEtag());
      metadataSchemaRecord.setAcl(dataResource.getAcls());

      for (edu.kit.datamanager.repo.domain.Date d : dataResource.getDates()) {
        if (edu.kit.datamanager.repo.domain.Date.DATE_TYPE.CREATED.equals(d.getType())) {
          LOG.trace("Creation date entry found.");
          metadataSchemaRecord.setCreatedAt(d.getValue());
          break;
        }
      }
      if (dataResource.getLastUpdate() != null) {
        metadataSchemaRecord.setLastUpdate(dataResource.getLastUpdate());
      }

      if (dataResource.getIdentifier() != null) {
        PrimaryIdentifier identifier = dataResource.getIdentifier();
        if (identifier.hasDoi()) {
          metadataSchemaRecord.setPid(identifier.getValue());
        }
      }
      metadataSchemaRecord.setSchemaVersion(applicationProperties.getAuditService().getCurrentVersion(dataResource.getId()));

      ContentInformation info;
      info = getContentInformationOfResource(applicationProperties, dataResource);

      if (info != null) {
        metadataSchemaRecord.setSchemaDocumentUri(info.getContentUri());
      }
    }
    return metadataSchemaRecord;
  }

  private static ContentInformation getContentInformationOfResource(RepoBaseConfiguration applicationProperties,
          DataResource dataResource) {
    ContentInformation returnValue = null;
    IContentInformationService contentInformationService = applicationProperties.getContentInformationService();
    ContentInformation info = new ContentInformation();
    info.setParentResource(dataResource);
    List<ContentInformation> listOfFiles = contentInformationService.findAll(info, PageRequest.of(0, 100)).getContent();
    if (LOG.isTraceEnabled()) {
      LOG.trace("Found {} files for resource '{}'", listOfFiles.size(), dataResource.getId());
      for (ContentInformation ci : listOfFiles) {
        DataResource parentResource = ci.getParentResource();
        ci.setParentResource(null);
        LOG.trace("ContentInformation: {}", ci);
        ci.setParentResource(parentResource);
      }
    }
    if (!listOfFiles.isEmpty()) {
      returnValue = listOfFiles.get(0);
    }
    return returnValue;
  }

  /**
   * Validate metadata document with given schema.
   *
   * @param record metadata of the document.
   * @param document document
   * @return ResponseEntity in case of an error.
   * @throws IOException Error reading document.
   */
  public static void validateMetadataDocument(MetastoreConfiguration metastoreProperties,
          MultipartFile document,
          String schemaId,
          Long version) {
    LOG.trace("validateMetadataDocument {},{}, {}", metastoreProperties, schemaId, document);

    if (document == null || document.isEmpty()) {
      String message = "Missing metadata document in body. Returning HTTP BAD_REQUEST.";
      LOG.error(message);
      throw new BadArgumentException(message);
    }
    MetadataSchemaRecord schemaRecord = getRecordByIdAndVersion(metastoreProperties, schemaId, version);
    try {
      //obtain validator for type
      IValidator applicableValidator = getValidatorForRecord(metastoreProperties, schemaRecord, null);

      if (applicableValidator == null) {
        String message = "No validator found for schema type " + schemaRecord.getType();
        LOG.error(message);
        throw new UnprocessableEntityException(message);
      } else {
        LOG.trace("Validator found. Checking local schema file.");
        Path schemaDocumentPath = Paths.get(URI.create(schemaRecord.getSchemaDocumentUri()));

        if (!Files.exists(schemaDocumentPath) || !Files.isRegularFile(schemaDocumentPath) || !Files.isReadable(schemaDocumentPath)) {
          LOG.error("Schema document with schemaId '{}'at path {} either does not exist or is no file or is not readable.", schemaRecord.getSchemaId(), schemaDocumentPath);
          throw new CustomInternalServerError("Schema document on server either does not exist or is no file or is not readable.");
        }

        LOG.trace("Performing validation of metadata document using schema {}, version {} and validator {}.", schemaRecord.getSchemaId(), schemaRecord.getSchemaVersion(), applicableValidator);
        if (!applicableValidator.validateMetadataDocument(schemaDocumentPath.toFile(), document.getInputStream())) {
          LOG.warn("Metadata document validation failed.");
          throw new UnprocessableEntityException(applicableValidator.getErrorMessage());
        }
      }
    } catch (IOException ex) {
      String message = "Failed to read metadata document from input stream.";
      LOG.error(message, ex);
      throw new UnprocessableEntityException(message);
    }
    LOG.trace("Metadata document validation succeeded.");
  }

  public static MetadataSchemaRecord getRecordByIdAndVersion(MetastoreConfiguration metastoreProperties,
          String recordId) throws ResourceNotFoundException {
    return getRecordByIdAndVersion(metastoreProperties, recordId, null);
  }

  public static MetadataSchemaRecord getRecordByIdAndVersion(MetastoreConfiguration metastoreProperties,
          String recordId, Long version) throws ResourceNotFoundException {
    //if security enabled, check permission -> if not matching, return HTTP UNAUTHORIZED or FORBIDDEN
    DataResource dataResource = metastoreProperties.getDataResourceService().findByAnyIdentifier(recordId, version);

    return migrateToMetadataSchemaRecord(metastoreProperties, dataResource);
  }

  public static Path getSchemaDocumentByIdAndVersion(MetastoreConfiguration metastoreProperties,
          String recordId) throws ResourceNotFoundException {
    return getSchemaDocumentByIdAndVersion(metastoreProperties, recordId, null);
  }

  public static Path getSchemaDocumentByIdAndVersion(MetastoreConfiguration metastoreProperties,
          String recordId, Long version) throws ResourceNotFoundException {
    LOG.trace("Obtaining metadata record with id {} and version {}.", recordId, version);
    MetadataSchemaRecord record = getRecordByIdAndVersion(metastoreProperties, recordId, version);

    URI schemaDocument = URI.create(record.getSchemaDocumentUri());

    Path schemaDocumentPath = Paths.get(schemaDocument);
    if (!Files.exists(schemaDocumentPath) || !Files.isRegularFile(schemaDocumentPath) || !Files.isReadable(schemaDocumentPath)) {
      LOG.warn("Schema document at path {} either does not exist or is no file or is not readable. Returning HTTP NOT_FOUND.", schemaDocumentPath);
      throw new CustomInternalServerError("Schema document on server either does not exist or is no file or is not readable.");
    }
    return schemaDocumentPath;
  }

  public static MetadataSchemaRecord mergeRecords(MetadataSchemaRecord managed, MetadataSchemaRecord provided) {
    if (provided != null) {
      if (!Objects.isNull(provided.getPid())) {
        LOG.trace("Updating pid from {} to {}.", managed.getPid(), provided.getPid());
        managed.setPid(provided.getPid());
      }

      //update acl
      if (provided.getAcl() != null) {
        LOG.trace("Updating record acl from {} to {}.", managed.getAcl(), provided.getAcl());
        managed.setAcl(provided.getAcl());
      }
      //update mimetype
      if (provided.getMimeType() != null) {
        LOG.trace("Updating record mimetype from {} to {}.", managed.getMimeType(), provided.getMimeType());
        managed.setMimeType(provided.getMimeType());
      }
      //update mimetype
      if (provided.getType() != null) {
        LOG.trace("Updating record type from {} to {}.", managed.getType(), provided.getType());
        managed.setType(provided.getType());
      }
    }
//    LOG.trace("Setting lastUpdate to now().");
//    managed.setLastUpdate(Instant.now());
    return managed;
  }

  public static void setToken(String bearerToken) {
    guestToken = bearerToken;
  }

  private static void validateMetadataSchemaDocument(MetastoreConfiguration metastoreProperties, MetadataSchemaRecord schemaRecord, MultipartFile document) {
    if (document == null || document.isEmpty()) {
      String message = "Missing metadata document in body. Returning HTTP BAD_REQUEST.";
      LOG.error(message);
      throw new BadArgumentException(message);
    }

    IValidator applicableValidator = null;
    try {
      applicableValidator = getValidatorForRecord(metastoreProperties, schemaRecord, document.getBytes());

      if (applicableValidator == null) {
        String message = "No validator found for schema type " + schemaRecord.getType() + ". Returning HTTP UNPROCESSABLE_ENTITY.";
        LOG.error(message);
        throw new UnprocessableEntityException(message);
      } else {
        LOG.trace("Validator found. Checking provided schema file.");
        LOG.trace("Performing validation of metadata document using schema {}, version {} and validator {}.", schemaRecord.getSchemaId(), schemaRecord.getSchemaVersion(), applicableValidator);
        if (!applicableValidator.isSchemaValid(document.getInputStream())) {
          String message = "Metadata document validation failed. Returning HTTP UNPROCESSABLE_ENTITY.";
          LOG.warn(message);
          throw new UnprocessableEntityException(message);
        }
      }
    } catch (IOException ex) {
      String message = "Failed to read metadata document from input stream.";
      LOG.error(message, ex);
      throw new UnprocessableEntityException(message);
    }

    LOG.trace("Schema document is valid!");
  }

  private static IValidator getValidatorForRecord(MetastoreConfiguration metastoreProperties, MetadataSchemaRecord schemaRecord, byte[] schemaDocument) {
    IValidator applicableValidator = null;
    //obtain/guess record type
    if (schemaRecord.getType() == null) {
      schemaRecord.setType(SchemaUtils.guessType(schemaDocument));
      if (schemaRecord.getType() == null) {
        String message = "Unable to detect schema type automatically. Please provide a valid type";
        LOG.error(message);
        throw new UnprocessableEntityException(message);
      } else {
        LOG.debug("Automatically detected schema type {}.", schemaRecord.getType());
      }
    }
    for (IValidator validator : metastoreProperties.getValidators()) {
      if (validator.supportsSchemaType(schemaRecord.getType())) {
        applicableValidator = validator;
        LOG.trace("Found validator for schema: '{}'", schemaRecord.getType().name());
        break;
      }
    }
    return applicableValidator;
  }
}
