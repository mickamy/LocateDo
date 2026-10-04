package handler

import (
	"context"

	"connectrpc.com/connect"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/errors/cerrors"
	"github.com/mickamy/LocateDo/internal/feature/category/mapper"
	"github.com/mickamy/LocateDo/internal/feature/category/usecase"
	categoryv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/category/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/category/v1/categoryv1connect"
	"github.com/mickamy/LocateDo/internal/lib/caller"
	"github.com/mickamy/LocateDo/internal/lib/ids"
)

type Category struct {
	_              di.Infra                `di:"embed"`
	putCategory    *usecase.PutCategory    `di:""`
	deleteCategory *usecase.DeleteCategory `di:""`
}

var _ categoryv1connect.CategoryServiceHandler = (*Category)(nil)

func (h *Category) PutCategory(
	ctx context.Context,
	req *connect.Request[categoryv1.PutCategoryRequest],
) (*connect.Response[categoryv1.PutCategoryResponse], error) {
	householdID, err := caller.HouseholdID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	target, err := ids.Parse("household_id", req.Msg.GetHouseholdId())
	if err != nil {
		return nil, cerrors.Map(err)
	}
	c, err := mapper.CategoryFromCategoryInput(req.Msg.GetCategory())
	if err != nil {
		return nil, cerrors.Map(aerrors.InvalidArgument(err.Error()))
	}
	c.HouseholdID = target

	if err := h.putCategory.Do(ctx, usecase.PutCategoryInput{HouseholdID: householdID, Category: c}); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&categoryv1.PutCategoryResponse{}), nil
}

func (h *Category) DeleteCategory(
	ctx context.Context,
	req *connect.Request[categoryv1.DeleteCategoryRequest],
) (*connect.Response[categoryv1.DeleteCategoryResponse], error) {
	householdID, err := caller.HouseholdID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	id, err := ids.Parse("id", req.Msg.GetId())
	if err != nil {
		return nil, cerrors.Map(err)
	}

	if err := h.deleteCategory.Do(ctx, usecase.DeleteCategoryInput{HouseholdID: householdID, CategoryID: id}); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&categoryv1.DeleteCategoryResponse{}), nil
}
