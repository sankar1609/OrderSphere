package com.ordersphere.orders.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Currency;
import java.util.regex.Pattern;

/** An upper-case ISO 4217 currency code that Java knows, e.g. USD or EUR (not "usd", "XYZ"). */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IsoCurrency.Validator.class)
public @interface IsoCurrency {

  String message() default "must be an ISO 4217 currency code such as USD or EUR";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<IsoCurrency, String> {

    private static final Pattern CODE = Pattern.compile("[A-Z]{3}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null) {
        return true; // @NotBlank reports a missing value
      }
      if (!CODE.matcher(value).matches()) {
        return false;
      }
      try {
        Currency.getInstance(value);
        return true;
      } catch (IllegalArgumentException unknown) {
        return false;
      }
    }
  }
}
