package com.switchbase.member;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.annotation.*;
import java.nio.charset.StandardCharsets;

/**
 * UTF-8 바이트 수 상한. bcrypt는 바이트 기준 72바이트까지만 쓰므로 한글은 글자 수보다 빨리 찬다.
 */
@Documented
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "입력이 너무 깁니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxUtf8Bytes, CharSequence> {

        private int max;

        @Override
        public void initialize(MaxUtf8Bytes annotation) {
            this.max = annotation.value();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null) {
                return true; // 빈 값은 @NotBlank가 판단한다
            }
            return value.toString().getBytes(StandardCharsets.UTF_8).length <= max;
        }
    }
}
