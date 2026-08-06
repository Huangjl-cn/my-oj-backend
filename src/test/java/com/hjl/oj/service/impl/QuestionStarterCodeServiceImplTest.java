package com.hjl.oj.service.impl;

import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.dto.question.QuestionStarterCodeSaveRequest;
import com.hjl.oj.model.entity.QuestionStarterCode;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import com.hjl.oj.utils.QuestionStarterCodeDefaults;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionStarterCodeServiceImplTest {

    private final QuestionStarterCodeServiceImpl service = new QuestionStarterCodeServiceImpl();

    @Test
    void missingRequestCreatesDefaultsForEverySupportedLanguage() {
        List<QuestionStarterCode> starterCodes = service.normalizeStarterCodes(null);

        assertEquals(QuestionSubmitLanguageEnum.values().length, starterCodes.size());
        assertTrue(starterCodes.stream().allMatch(item -> item.getStarterCode() != null
                && !item.getStarterCode().isBlank()));
    }

    @Test
    void blankStarterCodeUsesLanguageDefault() {
        List<QuestionStarterCodeSaveRequest> requests = defaultRequests();
        requests.get(0).setStarterCode("  \n");

        List<QuestionStarterCode> starterCodes = service.normalizeStarterCodes(requests);
        Map<String, QuestionStarterCode> starterCodeMap = starterCodes.stream()
                .collect(Collectors.toMap(QuestionStarterCode::getLanguage, Function.identity()));

        assertEquals(QuestionStarterCodeDefaults.getDefaultStarterCode(QuestionSubmitLanguageEnum.JAVA),
                starterCodeMap.get("java").getStarterCode());
    }

    @Test
    void missingLanguageIsRejectedWhenAListIsProvided() {
        List<QuestionStarterCodeSaveRequest> requests = defaultRequests();
        requests.remove(0);

        assertThrows(BusinessException.class, () -> service.normalizeStarterCodes(requests));
    }

    @Test
    void duplicateLanguageIsRejected() {
        List<QuestionStarterCodeSaveRequest> requests = defaultRequests();
        requests.add(request("java", "another template"));

        assertThrows(BusinessException.class, () -> service.normalizeStarterCodes(requests));
    }

    @Test
    void unsupportedLanguageIsRejected() {
        List<QuestionStarterCodeSaveRequest> requests = defaultRequests();
        requests.get(0).setLanguage("rust");

        assertThrows(BusinessException.class, () -> service.normalizeStarterCodes(requests));
    }

    private List<QuestionStarterCodeSaveRequest> defaultRequests() {
        return new ArrayList<>(Arrays.stream(QuestionSubmitLanguageEnum.values())
                .map(language -> request(language.getValue(), "custom " + language.getValue()))
                .toList());
    }

    private QuestionStarterCodeSaveRequest request(String language, String starterCode) {
        QuestionStarterCodeSaveRequest request = new QuestionStarterCodeSaveRequest();
        request.setLanguage(language);
        request.setStarterCode(starterCode);
        return request;
    }
}
