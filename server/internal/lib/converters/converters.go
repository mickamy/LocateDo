package converters

import (
	"time"
	"uuid"

	"github.com/go-kanna/kanna/mapper"
	"google.golang.org/protobuf/types/known/timestamppb"
)

func init() {
	mapper.Register(UUIDToString)
	mapper.RegisterE(uuid.Parse)
	mapper.Register(TimeToTimestamp)
	mapper.Register(TimestampToTime)
}

func UUIDToString(id uuid.UUID) string {
	return id.String()
}

func TimeToTimestamp(t time.Time) *timestamppb.Timestamp {
	return timestamppb.New(t)
}

// TimestampToTime maps an unset timestamp to the zero time, not the Unix epoch
// that (*timestamppb.Timestamp).AsTime returns for nil.
func TimestampToTime(ts *timestamppb.Timestamp) time.Time {
	if ts == nil {
		return time.Time{}
	}
	return ts.AsTime()
}
