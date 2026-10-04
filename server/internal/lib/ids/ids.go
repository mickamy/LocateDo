package ids

import (
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
)

// Parse reads a UUID sent by a client; field names it in the error.
func Parse(field, raw string) (uuid.UUID, error) {
	id, err := uuid.Parse(raw)
	if err != nil {
		return uuid.UUID{}, aerrors.InvalidArgument(fmt.Sprintf("%s: %v", field, err))
	}
	return id, nil
}
