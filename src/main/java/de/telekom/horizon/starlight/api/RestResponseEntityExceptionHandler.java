// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.api;

import de.telekom.eni.pandora.horizon.model.common.ProblemMessage;
import de.telekom.horizon.starlight.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@ControllerAdvice
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String HORIZON_PUBLISH_EVENTS_DOC_URL = "https://developer.telekom.de/docs/src/tardis_customer_handbook/horizon/step-by-step-guide/Publish_Events/#sending-events";
    public static final String DEFAULT_ERROR_TITLE = "Something went wrong.";

    private final ApplicationContext applicationContext;

    @Autowired
    public RestResponseEntityExceptionHandler(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    // 202 Accepted
    @ExceptionHandler(value = {
            UnknownEventTypeOrNoSubscriptionException.class
    })
    @ResponseStatus(HttpStatus.ACCEPTED)
    protected ResponseEntity<Object> handleOk(HorizonStarlightException e, WebRequest request) {
        return responseEntityForException(e, HttpStatus.ACCEPTED, request, null);
    }

    // 400 Bad request
    @ExceptionHandler(value = {
            EventNotCompliantWithSchemaException.class,
            InvalidEventBodyException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    protected ResponseEntity<Object> handleBadRequest(HorizonStarlightException e, WebRequest request) {
        return responseEntityForException(e, HttpStatus.BAD_REQUEST, request, null);
    }

    // 401 Unauthorized
    @ExceptionHandler(value = {
            AuthenticationException.class,
            RealmDoesNotMatchEnvironmentException.class,
    })
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    protected ResponseEntity<Object> handleUnauthorized(Exception e, WebRequest request) {
        return responseEntityForException(e, HttpStatus.UNAUTHORIZED, request, null);
    }

    // 403 Forbidden
    @ExceptionHandler(value = {
            PublisherDoesNotMatchEventTypeException.class,
    })
    @ResponseStatus(HttpStatus.FORBIDDEN)
    protected ResponseEntity<Object> handleForbidden(HorizonStarlightException e, WebRequest request) {
        return responseEntityForException(e, HttpStatus.FORBIDDEN, request, null);
    }

    // 413 Payload Too Large
    @ExceptionHandler(value = {
            PayloadTooLargeException.class,
    })
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    protected ResponseEntity<Object> handlePayloadTooLargeException(HorizonStarlightException e, WebRequest request) {
        return responseEntityForException(e, HttpStatus.PAYLOAD_TOO_LARGE, request, null);
    }

    // 504 Gateway Timeout
    @ExceptionHandler(CouldNotPublishEventMessageException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    protected ResponseEntity<Object> handleCouldNotPublishEventMessageException(CouldNotPublishEventMessageException e, WebRequest request) {
        log.error("Horizon Starlight error occurred while writing to kafka: " + e.getMessage(), e);

        return responseEntityForException(e, HttpStatus.GATEWAY_TIMEOUT, request, null);
    }

    // 500 Internal server error (known)
    @ExceptionHandler(HorizonStarlightException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected ResponseEntity<Object> handleHorizonStarlightException(HorizonStarlightException e, WebRequest request) {
        log.error("Horizon Starlight error occurred: " + e.getMessage(), e);

        return responseEntityForException(e, HttpStatus.INTERNAL_SERVER_ERROR, request, null);
    }

    // 500 Internal server error (unknown)
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    protected ResponseEntity<Object> handleAny(Exception e, WebRequest request) {
        log.error("Error occurred: " + e.getMessage(), e);

        var headers = new HttpHeaders();

        HttpStatusCode status;
        try {
            ResponseEntity<?> responseEntity = super.handleException(e, request);

            if (responseEntity != null) {
                status = responseEntity.getStatusCode();
                headers = responseEntity.getHeaders();
            } else {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
        } catch(Exception unknownException) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return responseEntityForException(e, status, request, headers);
    }

    private ResponseEntity<Object> responseEntityForException(Exception e, HttpStatusCode status, WebRequest request, @Nullable HttpHeaders headers) {
        var title = DEFAULT_ERROR_TITLE;
        var detail = "";

        if (e instanceof HorizonStarlightException) {
            title = e.getMessage();
        }

        if (e instanceof InvalidEventBodyException invalidEventBodyException) {
            detail = invalidEventBodyException.getDetail();
        }

        if (!(e instanceof HorizonStarlightException)) {
            detail = e.getMessage();
        }

        var message = new ProblemMessage(HORIZON_PUBLISH_EVENTS_DOC_URL, title);
        message.setStatus(status.value());
        message.setInstance(applicationContext.getId());

        if (StringUtils.hasText(detail)) {
            message.setDetail(detail);
        }


        var responseHeaders = new HttpHeaders();
        if (headers != null) {
            responseHeaders.addAll(headers);
        }

        responseHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        return handleExceptionInternal(e, message, responseHeaders, status, request);
    }
}