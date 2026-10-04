package webhook

import (
	"crypto/subtle"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"uuid"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/outbox"
)

const maxBodyBytes = 1 << 20

// RevenueCat takes RevenueCat's webhook as a prompt to re-check the users it
// names: it queues a sync_entitlement message per user and answers at once. The
// event type is not trusted to say what the plan should be.
type RevenueCat struct {
	_          di.Config         `di:"embed"`
	_          di.Infra          `di:"embed"`
	cfg        config.RevenueCat `di:""`
	transactor tx.Transactor     `di:""`
	messages   outbox.Repository `di:""`
}

var _ http.Handler = (*RevenueCat)(nil)

type revenueCatEvent struct {
	Event struct {
		Type              string   `json:"type"`
		AppUserID         string   `json:"app_user_id"`
		OriginalAppUserID string   `json:"original_app_user_id"`
		Aliases           []string `json:"aliases"`
		TransferredFrom   []string `json:"transferred_from"`
		TransferredTo     []string `json:"transferred_to"`
	} `json:"event"`
}

func (h *RevenueCat) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	if !h.authorized(r) {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}

	var body revenueCatEvent
	if err := json.NewDecoder(io.LimitReader(r.Body, maxBodyBytes)).Decode(&body); err != nil {
		http.Error(w, "malformed event", http.StatusBadRequest)
		return
	}
	userIDs := users(body)
	if body.Event.Type == "TEST" || len(userIDs) == 0 {
		w.WriteHeader(http.StatusOK)
		return
	}

	if err := h.transactor.WithTx(ctx, func(tx tx.Tx) error {
		messages := h.messages.Bind(tx)
		for _, id := range userIDs {
			if err := messages.Enqueue(ctx, outbox.SyncEntitlement(id, clock.Now(ctx))); err != nil {
				return fmt.Errorf("enqueue sync entitlement: %w", err)
			}
		}
		return nil
	}); err != nil {
		logger.Error(ctx, "revenuecat webhook failed", "error", err)
		http.Error(w, "try again", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusOK)
}

func (h *RevenueCat) authorized(r *http.Request) bool {
	if h.cfg.WebhookAuth == "" {
		return false
	}
	got := []byte(r.Header.Get("Authorization"))
	return subtle.ConstantTimeCompare(got, []byte(h.cfg.WebhookAuth)) == 1
}

// users collects every app user id in the event that is one of ours; ids
// RevenueCat made up for anonymous users are not UUIDs and drop out.
func users(body revenueCatEvent) []uuid.UUID {
	e := body.Event
	candidates := append([]string{e.AppUserID, e.OriginalAppUserID}, e.Aliases...)
	candidates = append(candidates, e.TransferredFrom...)
	candidates = append(candidates, e.TransferredTo...)

	seen := map[uuid.UUID]bool{}
	var out []uuid.UUID
	for _, c := range candidates {
		id, err := uuid.Parse(c)
		if err != nil || seen[id] {
			continue
		}
		seen[id] = true
		out = append(out, id)
	}
	return out
}
