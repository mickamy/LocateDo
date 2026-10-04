package ptr

func Map[T, U any](p *T, f func(T) U) *U {
	if p == nil {
		return nil
	}
	v := f(*p)
	return &v
}
