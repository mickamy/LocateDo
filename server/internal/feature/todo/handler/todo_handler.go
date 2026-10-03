package handler

import "github.com/mickamy/LocateDo/internal/gen/locatedo/todo/v1/todov1connect"

type Todo struct {
	todov1connect.UnimplementedTodoServiceHandler
}

var _ todov1connect.TodoServiceHandler = (*Todo)(nil)

func NewTodo() *Todo {
	return &Todo{}
}
