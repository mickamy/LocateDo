package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
)

const usage = `usage: admin <command> [flags]

commands:
  set-plan -user <user id> -plan <free|pro>   set the plan of the household the user owns`

func main() {
	if err := run(context.Background(), di.NewConfig(), os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run(ctx context.Context, cfg di.Config, args []string) error {
	if len(args) == 0 {
		return errors.New(usage)
	}
	switch args[0] {
	case "set-plan":
		return setPlan(ctx, cfg, args[1:])
	default:
		return fmt.Errorf("unknown command %q\n\n%s", args[0], usage)
	}
}

func setPlan(ctx context.Context, cfg di.Config, args []string) error {
	fs := flag.NewFlagSet("set-plan", flag.ContinueOnError)
	rawUser := fs.String("user", "", "id of the household's owner")
	rawPlan := fs.String("plan", "", "free or pro")
	if err := fs.Parse(args); err != nil {
		return fmt.Errorf("set-plan: %w", err)
	}

	userID, err := uuid.Parse(*rawUser)
	if err != nil {
		return fmt.Errorf("-user: %w", err)
	}
	plan := model.Plan(*rawPlan)
	if plan != model.PlanFree && plan != model.PlanPro {
		return fmt.Errorf("-plan must be free or pro, got %q", *rawPlan)
	}

	infra, err := di.NewInfra(ctx, cfg)
	if err != nil {
		return fmt.Errorf("build infrastructure: %w", err)
	}
	defer func() {
		_ = infra.Close()
	}()

	out, err := usecase.NewSetPlan(infra).Do(ctx, usecase.SetPlanInput{OwnerID: userID, Plan: plan})
	if err != nil {
		return fmt.Errorf("user %s: %w", userID, err)
	}
	if out.Changed {
		fmt.Printf("household %s: plan set to %s (1 household changed)\n", out.HouseholdID, plan)
	} else {
		fmt.Printf("household %s: plan was already %s (0 households changed)\n", out.HouseholdID, plan)
	}
	return nil
}
