package mapper

import (
	"github.com/go-kanna/kanna/mapper"

	"github.com/mickamy/LocateDo/internal/feature/device/model"
	devicev1 "github.com/mickamy/LocateDo/internal/gen/locatedo/device/v1"
)

func init() {
	mapper.Register(PlatformFromDevicev1)
}

func PlatformFromDevicev1(p devicev1.Platform) model.Platform {
	switch p {
	case devicev1.Platform_PLATFORM_IOS:
		return model.PlatformIOS
	case devicev1.Platform_PLATFORM_ANDROID:
		return model.PlatformAndroid
	case devicev1.Platform_PLATFORM_UNSPECIFIED:
		return ""
	default:
		return ""
	}
}
