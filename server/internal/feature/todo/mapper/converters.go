package mapper

import (
	"github.com/go-kanna/kanna/mapper"

	"github.com/mickamy/LocateDo/internal/feature/todo/model"
	todov1 "github.com/mickamy/LocateDo/internal/gen/locatedo/todo/v1"
)

func init() {
	mapper.Register(TriggerFromTodov1)
	mapper.Register(TriggerToTodov1)
}

// TriggerFromTodov1 reads an unset trigger or event as arrival, the default.
func TriggerFromTodov1(t *todov1.Trigger) model.Trigger {
	if t.GetEvent() == todov1.PlaceEvent_PLACE_EVENT_DEPARTURE {
		return model.Trigger{Event: model.PlaceEventDeparture}
	}
	return model.Trigger{Event: model.PlaceEventArrival}
}

func TriggerToTodov1(t model.Trigger) *todov1.Trigger {
	event := todov1.PlaceEvent_PLACE_EVENT_ARRIVAL
	if t.Event == model.PlaceEventDeparture {
		event = todov1.PlaceEvent_PLACE_EVENT_DEPARTURE
	}
	return &todov1.Trigger{Event: event}
}
