from PIL import Image
import io
from fastapi import UploadFile
from app.config import Config


class ImageProcessor:
    @staticmethod
    def validate_file(file: UploadFile) -> tuple:
        if not file.filename:
            return False, "文件名不能为空"
        
        ext = file.filename.rsplit('.', 1)[-1].lower()
        if ext not in Config.ALLOWED_EXTENSIONS:
            return False, f"不支持的文件格式: {ext}，支持: {Config.ALLOWED_EXTENSIONS}"
        
        return True, ""
    
    @staticmethod
    def read_image(file: UploadFile) -> Image.Image:
        contents = file.file.read()
        image = Image.open(io.BytesIO(contents)).convert('RGB')
        file.file.seek(0)
        return image
    
    @staticmethod
    def validate_image_size(image: Image.Image) -> tuple:
        min_size = 100
        if image.width < min_size or image.height < min_size:
            return False, f"图片尺寸过小: {image.width}x{image.height}，最小要求: {min_size}x{min_size}"
        return True, ""
