package logger_test

import (
	"bytes"
	"encoding/json"
	"log/slog"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/lib/execution"
	"github.com/mickamy/LocateDo/internal/lib/logger"
)

func TestInfo_carriesTheJobName(t *testing.T) { //nolint:paralleltest // swaps the process-wide default logger
	var buf bytes.Buffer
	previous := slog.Default()
	slog.SetDefault(slog.New(slog.NewJSONHandler(&buf, nil)))
	t.Cleanup(func() { slog.SetDefault(previous) })

	logger.Info(execution.SetJobName(t.Context(), "push_household"), "inside a job")
	logger.Info(t.Context(), "outside a job")

	lines := bytes.Split(bytes.TrimSpace(buf.Bytes()), []byte("\n"))
	require.Len(t, lines, 2)
	var inside, outside map[string]any
	require.NoError(t, json.Unmarshal(lines[0], &inside))
	require.NoError(t, json.Unmarshal(lines[1], &outside))
	assert.Equal(t, "push_household", inside["job_name"])
	assert.NotContains(t, outside, "job_name")
}
