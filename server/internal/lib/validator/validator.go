package validator

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"reflect"
	"strings"

	lib "github.com/go-playground/validator/v10"
)

var validator = lib.New()

type ValidationError struct {
	Fields map[string][]string
}

func (e *ValidationError) Error() string {
	return fmt.Sprintf("validation failed on %d field(s)", len(e.Fields))
}

func init() {
	validator.RegisterTagNameFunc(func(fld reflect.StructField) string {
		name := strings.SplitN(fld.Tag.Get("json"), ",", 2)[0]
		if name == "-" {
			return ""
		}
		return name
	})
}

func Struct(ctx context.Context, s any) error {
	if err := validator.StructCtx(ctx, s); err != nil {
		if errs, ok := errors.AsType[lib.ValidationErrors](err); ok {
			return mapValidationErrors(ctx, errs)
		}
		panic(fmt.Errorf("unknown validation error: %w", err))
	}
	return nil
}

func mapValidationErrors(ctx context.Context, errs lib.ValidationErrors) *ValidationError {
	messages := map[string][]string{}
	for _, err := range errs {
		var message string
		switch err.ActualTag() {
		case "required":
			message = "is required."
		case "min":
			message = fmt.Sprintf("is too short. (min=%s)", err.Param())
		case "max":
			message = fmt.Sprintf("is too long. (max=%s)", err.Param())
		case "email":
			message = "is not a valid email."
		case "url":
			message = "is not a valid URL."
		default:
			slog.WarnContext(ctx, "unknown validation tag", "tag", err.ActualTag())
			message = "is invalid."
		}

		field := err.Field()
		messages[field] = append(messages[field], message)
	}
	return &ValidationError{Fields: messages}
}
