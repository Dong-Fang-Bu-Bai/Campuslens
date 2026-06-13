import tempfile
import unittest
from pathlib import Path

import numpy as np

from app.utils.faiss_manager import FAISSManager


class FaissPersistenceTests(unittest.TestCase):
    def test_round_trip_works_in_unicode_directory(self):
        with tempfile.TemporaryDirectory(prefix="campuslens-faiss-") as tmp:
            index_dir = Path(tmp) / "中文索引"
            manager = FAISSManager(4, index_dir)
            manager.create_index()
            manager.add_vectors(
                np.asarray([[1.0, 0.0, 0.0, 0.0]], dtype=np.float32),
                [{"landmark_code": "L01"}],
            )
            manager.save_index()

            restored = FAISSManager(4, index_dir)
            self.assertTrue(restored.load_index())
            self.assertEqual(1, restored.index.ntotal)
            self.assertEqual("L01", restored.metadata[0]["landmark_code"])


if __name__ == "__main__":
    unittest.main()
