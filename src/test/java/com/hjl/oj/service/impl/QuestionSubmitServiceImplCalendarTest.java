package com.hjl.oj.service.impl;

import com.hjl.oj.mapper.QuestionSubmitMapper;
import com.hjl.oj.model.dto.questionsubmit.SubmitDayRow;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.enums.JudgeResultEnum;
import com.hjl.oj.model.vo.SubmitCalendarVO;
import com.hjl.oj.model.vo.SubmitDayVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionSubmitServiceImplCalendarTest {

    private static final String ACCEPTED = JudgeResultEnum.ACCEPTED.getSqlFragment();

    @Mock
    private QuestionSubmitMapper questionSubmitMapper;

    @InjectMocks
    private QuestionSubmitServiceImpl questionSubmitService;

    private final User user = new User();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(questionSubmitService, "baseMapper", questionSubmitMapper);
        user.setId(7L);
    }

    @Test
    void calendarListsSubmitDaysAndTotals() {
        when(questionSubmitMapper.selectUserSubmitDays(ACCEPTED, 7L, 365))
                .thenReturn(List.of(day("2026-11-24", 3L, 1L), day("2026-11-25", 2L, null)));
        when(questionSubmitMapper.selectUserSubmitTotals(ACCEPTED, 7L)).thenReturn(day(null, 12L, 5L));

        SubmitCalendarVO calendar = questionSubmitService.getMySubmitCalendar(user, 365);

        assertEquals(List.of(new SubmitDayVO("2026-11-24", 3, 1), new SubmitDayVO("2026-11-25", 2, 0)),
                calendar.getDays());
        assertEquals(365, calendar.getRangeDays());
        assertEquals(12, calendar.getTotalSubmits());
        assertEquals(5, calendar.getAcceptedSubmits());
    }

    @Test
    void rangeIsClampedAndMissingTotalsCountAsZero() {
        when(questionSubmitMapper.selectUserSubmitDays(eq(ACCEPTED), eq(7L), anyInt())).thenReturn(List.of());

        SubmitCalendarVO tooLong = questionSubmitService.getMySubmitCalendar(user, 5000);
        SubmitCalendarVO tooShort = questionSubmitService.getMySubmitCalendar(user, 1);

        assertEquals(366, tooLong.getRangeDays());
        assertEquals(7, tooShort.getRangeDays());
        assertEquals(0, tooLong.getTotalSubmits());
        assertEquals(0, tooLong.getAcceptedSubmits());
        verify(questionSubmitMapper).selectUserSubmitDays(ACCEPTED, 7L, 366);
        verify(questionSubmitMapper).selectUserSubmitDays(ACCEPTED, 7L, 7);
    }

    private static SubmitDayRow day(String date, Long submits, Long accepted) {
        SubmitDayRow row = new SubmitDayRow();
        row.setDay(date);
        row.setSubmitCount(submits);
        row.setAcceptedCount(accepted);
        return row;
    }
}
