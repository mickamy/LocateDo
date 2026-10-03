package db

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5/pgxpool"
)

func New(ctx context.Context, url string) (*pgxpool.Pool, error) {
	pool, err := pgxpool.New(ctx, url)
	if err != nil {
		return nil, fmt.Errorf("create pool: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("ping database: %w", err)
	}
	return pool, nil
}

type Writer struct {
	*pgxpool.Pool
}

type Reader struct {
	*pgxpool.Pool
}

func NewWriter(ctx context.Context, url string) (Writer, error) {
	pool, err := New(ctx, url)
	if err != nil {
		return Writer{}, err
	}
	return Writer{Pool: pool}, nil
}

func NewReader(ctx context.Context, url string) (Reader, error) {
	pool, err := New(ctx, url)
	if err != nil {
		return Reader{}, err
	}
	return Reader{Pool: pool}, nil
}
