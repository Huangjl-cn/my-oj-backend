package com.hjl.oj.model.dto.questionlist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 往题单里加一道题，或从题单里拿掉一道题
 */
@Data
public class QuestionListItemRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long listId;

    private Long questionId;
}
