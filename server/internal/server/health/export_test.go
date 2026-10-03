package health

type Pinger = pinger

func NewHealthWithPingers(pingers map[string]Pinger) Health {
	return Health{pingers: pingers}
}
