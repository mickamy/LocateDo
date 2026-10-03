package handler

import "github.com/mickamy/LocateDo/internal/gen/locatedo/category/v1/categoryv1connect"

type Category struct {
	categoryv1connect.UnimplementedCategoryServiceHandler
}

var _ categoryv1connect.CategoryServiceHandler = (*Category)(nil)

func NewCategory() *Category {
	return &Category{}
}
