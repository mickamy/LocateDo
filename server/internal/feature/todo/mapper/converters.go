package mapper

import (
	"fmt"

	"github.com/go-kanna/kanna/mapper"

	"github.com/mickamy/LocateDo/internal/feature/todo/model"
	todov1 "github.com/mickamy/LocateDo/internal/gen/locatedo/todo/v1"
)

func init() {
	mapper.RegisterE(TriggerFromTodov1)
	mapper.Register(TriggerToTodov1)
}

func TriggerFromTodov1(t *todov1.Trigger) (model.Trigger, error) {
	switch t.GetEvent() {
	case todov1.PlaceEvent_PLACE_EVENT_ARRIVAL:
		return model.Trigger{Event: model.PlaceEventArrival}, nil
	case todov1.PlaceEvent_PLACE_EVENT_DEPARTURE:
		return model.Trigger{Event: model.PlaceEventDeparture}, nil
	case todov1.PlaceEvent_PLACE_EVENT_UNSPECIFIED:
		return model.Trigger{}, fmt.Errorf("unexpected place event %v", t.GetEvent())
	default:
		return model.Trigger{}, fmt.Errorf("unexpected place event %v", t.GetEvent())
	}
}

func TriggerToTodov1(t model.Trigger) *todov1.Trigger {
	event := todov1.PlaceEvent_PLACE_EVENT_ARRIVAL
	if t.Event == model.PlaceEventDeparture {
		event = todov1.PlaceEvent_PLACE_EVENT_DEPARTURE
	}
	return &todov1.Trigger{Event: event}
}
