package job_test

import (
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/worker/job"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestReportOutbox_Run(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)

	// act
	err := job.NewReportOutbox(d.Infra()).Run(t.Context())

	// assert
	require.NoError(t, err)
}
