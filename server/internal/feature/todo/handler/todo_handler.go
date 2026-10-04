package handler

import (
	"context"
	"time"

	"connectrpc.com/connect"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/errors/cerrors"
	"github.com/mickamy/LocateDo/internal/feature/todo/mapper"
	"github.com/mickamy/LocateDo/internal/feature/todo/usecase"
	todov1 "github.com/mickamy/LocateDo/internal/gen/locatedo/todo/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/todo/v1/todov1connect"
	"github.com/mickamy/LocateDo/internal/lib/caller"
	"github.com/mickamy/LocateDo/internal/lib/ids"
)

type Todo struct {
	_                 di.Infra                   `di:"embed"`
	putTodo           *usecase.PutTodo           `di:""`
	setTodoCompletion *usecase.SetTodoCompletion `di:""`
	deleteTodo        *usecase.DeleteTodo        `di:""`
}

var _ todov1connect.TodoServiceHandler = (*Todo)(nil)

func (h *Todo) PutTodo(
	ctx context.Context,
	req *connect.Request[todov1.PutTodoRequest],
) (*connect.Response[todov1.PutTodoResponse], error) {
	householdID, err := caller.HouseholdID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	target, err := ids.Parse("household_id", req.Msg.GetHouseholdId())
	if err != nil {
		return nil, cerrors.Map(err)
	}
	td, err := mapper.TodoFromTodoInput(req.Msg.GetTodo())
	if err != nil {
		return nil, cerrors.Map(aerrors.InvalidArgument(err.Error()))
	}
	td.HouseholdID = target

	if err := h.putTodo.Do(ctx, usecase.PutTodoInput{HouseholdID: householdID, Todo: td}); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&todov1.PutTodoResponse{}), nil
}

func (h *Todo) SetTodoCompletion(
	ctx context.Context,
	req *connect.Request[todov1.SetTodoCompletionRequest],
) (*connect.Response[todov1.SetTodoCompletionResponse], error) {
	householdID, err := caller.HouseholdID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	id, err := ids.Parse("id", req.Msg.GetId())
	if err != nil {
		return nil, cerrors.Map(err)
	}
	var completedAt *time.Time
	if ts := req.Msg.GetCompletedAt(); ts != nil {
		at := ts.AsTime()
		completedAt = &at
	}

	if err := h.setTodoCompletion.Do(ctx, usecase.SetTodoCompletionInput{
		HouseholdID: householdID,
		TodoID:      id,
		CompletedAt: completedAt,
	}); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&todov1.SetTodoCompletionResponse{}), nil
}

func (h *Todo) DeleteTodo(
	ctx context.Context,
	req *connect.Request[todov1.DeleteTodoRequest],
) (*connect.Response[todov1.DeleteTodoResponse], error) {
	householdID, err := caller.HouseholdID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	id, err := ids.Parse("id", req.Msg.GetId())
	if err != nil {
		return nil, cerrors.Map(err)
	}

	if err := h.deleteTodo.Do(ctx, usecase.DeleteTodoInput{HouseholdID: householdID, TodoID: id}); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&todov1.DeleteTodoResponse{}), nil
}
