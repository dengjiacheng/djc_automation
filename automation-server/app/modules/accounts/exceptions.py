"""Account domain specific exceptions."""


class AccountError(Exception):
    """Base class for account domain errors."""


class AccountAlreadyExistsError(AccountError):
    """Raised when attempting to create an account with duplicate username."""


class AccountNotFoundError(AccountError):
    """Raised when the requested account cannot be found."""
