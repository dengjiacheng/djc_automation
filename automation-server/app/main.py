from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates

from app import __version__
from app.api import create_api_router
from app.api.routers import websocket as websocket_router
from app.core.config import get_settings
from app.db.session import init_db

settings = get_settings()
BASE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = BASE_DIR.parent


def _resolve_path(path: Path) -> Path:
    if path.is_absolute():
        return path
    return (PROJECT_ROOT / path).resolve()


STATIC_DIR = _resolve_path(settings.static_dir)
TEMPLATE_DIR = _resolve_path(settings.template_dir)
templates = Jinja2Templates(directory=str(TEMPLATE_DIR))


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    yield


def create_app() -> FastAPI:
    app = FastAPI(
        title=settings.project_name,
        description="Android 设备远程控制服务端",
        version=__version__,
        lifespan=lifespan,
    )

    # 用于静态资源缓存控制（前端引用时追加 ?v=xxx）
    app.state.static_version = __version__

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    if STATIC_DIR.exists():
        app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")

    app.include_router(create_api_router(settings.api_prefix))
    app.include_router(websocket_router.router)

    @app.get("/", response_class=HTMLResponse)
    async def homepage(request: Request):
        return templates.TemplateResponse("index.html", {"request": request})

    @app.get("/login", response_class=HTMLResponse)
    async def login_page(request: Request):
        return templates.TemplateResponse("login.html", {"request": request})

    @app.get("/customer", response_class=HTMLResponse)
    async def customer_dashboard(request: Request):
        return templates.TemplateResponse("customer.html", {"request": request})

    @app.get("/admin", response_class=HTMLResponse)
    async def admin_page(request: Request):
        return templates.TemplateResponse("admin.html", {"request": request})

    @app.get("/control", response_class=HTMLResponse)
    async def control_page(request: Request):
        return templates.TemplateResponse("control.html", {"request": request})

    return app


app = create_app()
