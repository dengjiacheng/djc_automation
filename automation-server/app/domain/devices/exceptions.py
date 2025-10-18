"""Device domain specific exceptions."""


class DeviceError(Exception):
    """Base class for device related domain errors."""


class DeviceAlreadyExistsError(DeviceError):
    """Raised when attempting to create a device with an existing username."""


class DeviceNotFoundError(DeviceError):
    """Raised when the requested device could not be found."""


class DeviceOwnershipError(DeviceError):
    """Raised when device ownership does not match expected account."""
