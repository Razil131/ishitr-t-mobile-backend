package ru.tmobile.backend.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import ru.tmobile.backend.dto.auth.LogoutRequest;
import ru.tmobile.backend.dto.auth.RefreshRequest;
import ru.tmobile.backend.dto.auth.RequestCodeRequest;
import ru.tmobile.backend.dto.auth.UpdateUserRequest;
import ru.tmobile.backend.dto.auth.VerifyCodeRequest;
import ru.tmobile.backend.dto.familyGroup.AddMemberRequest;
import ru.tmobile.backend.dto.familyGroup.FamilyGroupCreateRequest;
import ru.tmobile.backend.dto.familyGroup.TransferOwnershipRequest;

class DtoValidationTest {
    static Validator validator;

    static ValidatorFactory factory;

    @BeforeAll static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    private <T> Set<String> fields(Set<ConstraintViolation<T>> v) {
        return v.stream().map(c -> c.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test void requestCodeAcceptsValidPhone() {
        assertTrue(validator.validate(new RequestCodeRequest("+79991234567")).isEmpty());
    }

    @Test void requestCodeRejectsBlankPhone() {
        Set<ConstraintViolation<RequestCodeRequest>> v = validator.validate(new RequestCodeRequest(""));
        assertFalse(v.isEmpty());
        assertTrue(fields(v).contains("phone_number"));
    }

    @Test void requestCodeRejectsNullPhone() {
        Set<ConstraintViolation<RequestCodeRequest>> v = validator.validate(new RequestCodeRequest(null));
        assertFalse(v.isEmpty());
    }

    @Test void requestCodeRejectsWrongCountryCode() {
        assertFalse(validator.validate(new RequestCodeRequest("89991234567")).isEmpty());
    }

    @Test void requestCodeRejectsWrongLength() {
        assertFalse(validator.validate(new RequestCodeRequest("+7999123456")).isEmpty());
        assertFalse(validator.validate(new RequestCodeRequest("+799912345678")).isEmpty());
    }

    @Test void verifyCodeRequiresSessionAndCode() {
        Set<ConstraintViolation<VerifyCodeRequest>> v1 = validator.validate(new VerifyCodeRequest(null, "0000"));
        Set<ConstraintViolation<VerifyCodeRequest>> v2 = validator.validate(new VerifyCodeRequest(UUID.randomUUID(), ""));
        assertTrue(fields(v1).contains("session_id"));
        assertTrue(fields(v2).contains("code"));
    }

    @Test void verifyCodeAcceptsValidPayload() {
        assertTrue(validator.validate(new VerifyCodeRequest(UUID.randomUUID(), "0000")).isEmpty());
    }

    @Test void refreshRequiresNonBlankToken() {
        assertFalse(validator.validate(new RefreshRequest("")).isEmpty());
        assertFalse(validator.validate(new RefreshRequest(null)).isEmpty());
        assertTrue(validator.validate(new RefreshRequest("token")).isEmpty());
    }

    @Test void logoutRequiresNonBlankToken() {
        assertFalse(validator.validate(new LogoutRequest("")).isEmpty());
        assertTrue(validator.validate(new LogoutRequest("token")).isEmpty());
    }

    @Test void familyGroupCreateAccepts1to3Members() {
        assertTrue(validator.validate(new FamilyGroupCreateRequest(1L, List.of(2L))).isEmpty());
        assertTrue(validator.validate(new FamilyGroupCreateRequest(1L, List.of(2L,3L,4L))).isEmpty());
    }

    @Test void familyGroupCreateRejectsEmptyMembers() {
        assertFalse(validator.validate(new FamilyGroupCreateRequest(1L, List.of())).isEmpty());
    }

    @Test void familyGroupCreateRejectsMoreThan3Members() {
        assertFalse(validator.validate(new FamilyGroupCreateRequest(1L, List.of(2L,3L,4L,5L))).isEmpty());
    }

    @Test void familyGroupCreateRejectsNullOwner() {
        assertFalse(validator.validate(new FamilyGroupCreateRequest(null, List.of(2L))).isEmpty());
    }

    @Test void addMemberRequiresUserId() {
        assertFalse(validator.validate(new AddMemberRequest(null)).isEmpty());
        assertTrue(validator.validate(new AddMemberRequest(5L)).isEmpty());
    }

    @Test void transferOwnershipRequiresNewOwnerId() {
        assertFalse(validator.validate(new TransferOwnershipRequest(null)).isEmpty());
        assertTrue(validator.validate(new TransferOwnershipRequest(5L)).isEmpty());
    }

    @Test void updateUserAcceptsValidName() {
        assertTrue(validator.validate(new UpdateUserRequest("Ivan")).isEmpty());
    }

    @Test void updateUserRejectsBlankName() {
        assertFalse(validator.validate(new UpdateUserRequest("")).isEmpty());
    }

    @Test void updateUserRejectsTooLongName() {
        assertFalse(validator.validate(new UpdateUserRequest("a".repeat(51))).isEmpty());
    }

    @Test void consumptionEventDtoRejectsBlankEventId() {
        ConsumptionEventDto dto = new ConsumptionEventDto("", 1L, "TRAFFIC", 10.0, "2026-01-01T00:00:00Z", null);
        assertFalse(validator.validate(dto).isEmpty());
    }

    @Test void consumptionEventDtoRejectsNullUserId() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e1", null, "TRAFFIC", 10.0, "2026-01-01T00:00:00Z", null);
        assertFalse(validator.validate(dto).isEmpty());
    }

    @Test void consumptionEventDtoRejectsBlankResourceType() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e1", 1L, "", 10.0, "2026-01-01T00:00:00Z", null);
        assertFalse(validator.validate(dto).isEmpty());
    }

    @Test void consumptionEventDtoRejectsNullAmount() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e1", 1L, "TRAFFIC", null, "2026-01-01T00:00:00Z", null);
        assertFalse(validator.validate(dto).isEmpty());
    }

    @Test void consumptionEventDtoRejectsBlankTimestamp() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e1", 1L, "TRAFFIC", 10.0, "", null);
        assertFalse(validator.validate(dto).isEmpty());
    }

    @Test void consumptionEventDtoAcceptsFullValidPayload() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e1", 1L, "TRAFFIC", 10.0, "2026-01-01T00:00:00Z", 5L);
        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test void consumptionEventDtoAcceptsNullGroupId() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e1", 1L, "MINUTES", 1.0, "2026-01-01T00:00:00Z", null);
        assertTrue(validator.validate(dto).isEmpty());
    }
}