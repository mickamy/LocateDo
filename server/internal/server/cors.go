package server

import (
	"net/http"
	"slices"
)

// allowCORS lets the given origins call the given paths from a browser and
// answers their preflight requests. Other requests pass through untouched.
func allowCORS(next http.Handler, origins []string, paths ...string) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")
		if origin == "" || !slices.Contains(paths, r.URL.Path) || !slices.Contains(origins, origin) {
			next.ServeHTTP(w, r)
			return
		}

		w.Header().Set("Access-Control-Allow-Origin", origin)
		w.Header().Add("Vary", "Origin")
		if r.Method != http.MethodOptions {
			next.ServeHTTP(w, r)
			return
		}
		w.Header().Set("Access-Control-Allow-Methods", http.MethodPost)
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Connect-Protocol-Version, Connect-Timeout-Ms")
		w.Header().Set("Access-Control-Max-Age", "7200")
		w.WriteHeader(http.StatusNoContent)
	})
}
