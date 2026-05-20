import faiss
import numpy as np
from pathlib import Path
from typing import List, Tuple
import pickle
from app.config import Config


class FAISSManager:
    def __init__(self, dimension: int):
        self.dimension = dimension
        self.index = None
        self.metadata = []
        self.index_path = Config.FAISS_INDEX_DIR / "landmark_index.faiss"
        self.metadata_path = Config.FAISS_INDEX_DIR / "metadata.pkl"
    
    def create_index(self):
        self.index = faiss.IndexFlatIP(self.dimension)
        print(f"Created new FAISS index with dimension {self.dimension}")
    
    def load_index(self) -> bool:
        if self.index_path.exists() and self.metadata_path.exists():
            self.index = faiss.read_index(str(self.index_path))
            with open(self.metadata_path, 'rb') as f:
                self.metadata = pickle.load(f)
            print(f"Loaded FAISS index with {len(self.metadata)} vectors")
            return True
        return False
    
    def save_index(self):
        Config.FAISS_INDEX_DIR.mkdir(parents=True, exist_ok=True)
        faiss.write_index(self.index, str(self.index_path))
        with open(self.metadata_path, 'wb') as f:
            pickle.dump(self.metadata, f)
        print(f"Saved FAISS index with {len(self.metadata)} vectors")
    
    def add_vectors(self, vectors: np.ndarray, metadata_list: List[dict]):
        if self.index is None:
            self.create_index()
        
        vectors = vectors.astype('float32')
        faiss.normalize_L2(vectors)
        
        self.index.add(vectors)
        self.metadata.extend(metadata_list)
    
    def search(self, query_vector: np.ndarray, top_k: int = 5) -> List[Tuple[dict, float]]:
        if self.index is None or self.index.ntotal == 0:
            raise ValueError("FAISS 索引为空，请先构建索引")
        
        query_vector = query_vector.astype('float32').reshape(1, -1)
        faiss.normalize_L2(query_vector)
        
        scores, indices = self.index.search(query_vector, top_k)
        
        results = []
        for score, idx in zip(scores[0], indices[0]):
            if idx != -1:
                results.append((self.metadata[idx], float(score)))
        
        return results
    
    def rebuild_from_scratch(self, all_vectors: np.ndarray, all_metadata: List[dict]):
        self.create_index()
        self.metadata = []
        self.add_vectors(all_vectors, all_metadata)
        self.save_index()
    
    def get_index_stats(self) -> dict:
        if self.index is None:
            return {"status": "not_initialized"}
        
        return {
            "status": "ready",
            "totalVectors": self.index.ntotal,
            "dimension": self.dimension,
            "indexedLandmarks": len(set(m.get('landmark_code') for m in self.metadata))
        }
