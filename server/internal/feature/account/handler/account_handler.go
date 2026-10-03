package handler

import "github.com/mickamy/LocateDo/internal/gen/locatedo/account/v1/accountv1connect"

type Account struct {
	accountv1connect.UnimplementedAccountServiceHandler
}

var _ accountv1connect.AccountServiceHandler = (*Account)(nil)

func NewAccount() *Account {
	return &Account{}
}
