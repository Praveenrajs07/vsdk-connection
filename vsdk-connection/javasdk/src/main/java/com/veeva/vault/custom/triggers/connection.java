package com.veeva.vault.custom.action;

import java.math.BigDecimal; // Add this line
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.document.*;
import com.veeva.vault.sdk.api.notification.NotificationParameters;
import com.veeva.vault.sdk.api.notification.NotificationService;
import com.veeva.vault.sdk.api.notification.NotificationTemplate;
import com.veeva.vault.sdk.api.http.*;
import com.veeva.vault.sdk.api.json.JsonData;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@DocumentActionInfo(name = "vsdk_docaction_create_records__c", label = "Run vSDK Create Records Entry Action")
public class CreateRecordsDocumentEntryAction implements DocumentAction {

    public void execute(DocumentActionContext documentActionContext) {
        RequestContext requestContext = RequestContext.get();
        RecordService recordService = ServiceLocator.locate(RecordService.class);
        DocumentService docService = ServiceLocator.locate(DocumentService.class);
        NotificationService notificationService = ServiceLocator.locate(NotificationService.class);
        HttpService httpService = ServiceLocator.locate(HttpService.class);

        DocumentVersion docVersion = documentActionContext.getDocumentVersions().get(0);
        List<Record> recordList = VaultCollections.newList();
        List<DocumentVersion> docVersionList = VaultCollections.newList();

        String id = docVersion.getValue("id", ValueType.STRING);
        BigDecimal majorVersion = docVersion.getValue("major_version_number_v", ValueType.NUMBER);
        BigDecimal minorVersion = docVersion.getValue("minor_version_number__v", ValueType.NUMBER);

        // Check if values are not null before using them
//        if (id != null && majorVersion != null && minorVersion != null) {
//            id += "_" + majorVersion.toString() + minorVersion.toString();
//        } else {
//            throw new RollbackException("NULL_VALUE_ERROR", "Document version fields are missing.");
//        }

        String documentName = retrieveDocumentName(httpService, id); // Retrieve document name via API

        // Create new record for "vSDK Document Task"
        Record record = recordService.newRecord("atestuser5__c");
        record.setValue("name__v", "vSDK Document Task " + Instant.now().toEpochMilli());
        recordList.add(record);

        // Save records and handle notifications on success
        recordService.batchSaveRecords(recordList)
                .onErrors(batchOperationErrors -> {
                    batchOperationErrors.stream().findFirst().ifPresent(error -> {
                        String errMsg = error.getError().getMessage();
                        int errPosition = error.getInputPosition();
                        String name = recordList.get(errPosition).getValue("name__v", ValueType.STRING);
                        throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to create vSDK Document Task records: "
                                + name + " due to " + errMsg);
                    });
                }).onSuccesses(successMessage -> {
                    Set<String> notificationUsers = VaultCollections.newSet();
                    successMessage.stream().forEach(positionalRecordId -> {
                        docVersion.setValue("title__v", "vSDK Document Task: " + positionalRecordId.getRecordId());

                        NotificationParameters notificationParameters = notificationService.newNotificationParameters();
                        notificationUsers.add(requestContext.getCurrentUserId());
                        notificationParameters.setRecipientsByUserIds(notificationUsers);
                        notificationParameters.setSenderId(requestContext.getCurrentUserId());

                        NotificationTemplate template = notificationService.newNotificationTemplate()
                                .setTemplateName("veevasdk_test5__c")
                                .setTokenValue("document_name", documentName);

                        notificationService.send(notificationParameters, template);
                    });
                })
                .execute();

        docVersionList.add(docVersion);
        docService.saveDocumentVersions(docVersionList);
    }

    private String retrieveDocumentName(HttpService httpService, String documentId) {
        final String[] documentName = new String[1];  // Temporary storage

        HttpRequest httpRequest = httpService.newHttpRequest("spr_5");
        httpRequest.setMethod(HttpMethod.GET);
//        httpRequest.appendPath("module=account&action=balance&address=0xde0b295669a9fd93d5f28d9ec85e40f4cb697bae&tag=latest&apikey=1P51DFP2UN6YWRDGHRGHZTPFAD512WKNRE");
        httpRequest.setBodyParam("module", "account");
        httpRequest.setBodyParam("action", "balance");
        httpRequest.setBodyParam("address", "0xde0b295669a9fd93d5f28d9ec85e40f4cb697bae");
        httpRequest.setBodyParam("tag", "latest");

        httpService.send(httpRequest, HttpResponseBodyValueType.JSONDATA)
                .onError(e -> {
                    throw new RollbackException("DOCUMENT_QUERY_ERROR", e.getMessage());
                })
                .onSuccess(httpResponse -> {
                    JsonData jsonData = httpResponse.getResponseBody();
                    JsonObject jsonObject = jsonData.getJsonObject();
//                    JsonObject jsonObject1 = jsonObject.getValue("document", JsonValueType.OBJECT);
                    documentName[0] = jsonObject.getValue("result", JsonValueType.STRING);  // Store the name
                }).execute();

        return documentName[0];  // Return the stored name
    }

    public boolean isExecutable(DocumentActionContext documentActionContext) {
        return true;
    }
}
