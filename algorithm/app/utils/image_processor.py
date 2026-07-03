from PIL import Image, UnidentifiedImageError
import io
from fastapi import UploadFile
from app.config import Config


class ImageProcessor:
    @staticmethod
    def _file_size_error() -> str:
        size_mb = Config.MAX_FILE_SIZE / (1024 * 1024)
        limit = f"{size_mb:g}MB" if size_mb >= 1 else f"{Config.MAX_FILE_SIZE} bytes"
        return f"图片大小不能超过 {limit}"

    @staticmethod
    def validate_file(file: UploadFile) -> tuple:
        if not file.filename:
            return False, "文件名不能为空"
        
        ext = file.filename.rsplit('.', 1)[-1].lower()
        if ext not in Config.ALLOWED_EXTENSIONS:
            return False, f"不支持的文件格式: {ext}，支持: {Config.ALLOWED_EXTENSIONS}"

        file_size = getattr(file, "size", None)
        if file_size is not None and file_size > Config.MAX_FILE_SIZE:
            return False, ImageProcessor._file_size_error()
        
        return True, ""
    
    @staticmethod
    def read_image(file: UploadFile) -> Image.Image:
        contents = file.file.read()
        if len(contents) > Config.MAX_FILE_SIZE:
            file.file.seek(0)
            raise ValueError(ImageProcessor._file_size_error())
        try:
            image = Image.open(io.BytesIO(contents)).convert('RGB')
        except (UnidentifiedImageError, OSError) as exc:
            raise ValueError("无法读取图片内容") from exc
        file.file.seek(0)
        return image
    
    @staticmethod
    def validate_image_size(image: Image.Image) -> tuple:
        min_size = 100
        if image.width < min_size or image.height < min_size:
            return False, f"图片尺寸过小: {image.width}x{image.height}，最小要求: {min_size}x{min_size}"
        return True, ""
