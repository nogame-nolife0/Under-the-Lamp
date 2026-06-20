from pathlib import Path

import chromadb

from app.config import get_settings

COLLECTION_NAME = "questions"


class ChromaStore:
    def __init__(self) -> None:
        settings = get_settings()
        persist_dir = Path(settings.chroma_persist_dir)
        persist_dir.mkdir(parents=True, exist_ok=True)
        self._client = chromadb.PersistentClient(path=str(persist_dir))
        self._collection = self._client.get_or_create_collection(
            name=COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"},
        )

    @property
    def count(self) -> int:
        return self._collection.count()

    def upsert(
        self,
        question_id: int,
        embedding: list[float],
        document: str,
        metadata: dict[str, str | int | float | bool],
    ) -> None:
        clean_meta = {k: v for k, v in metadata.items() if v is not None and v != ""}
        self._collection.upsert(
            ids=[str(question_id)],
            embeddings=[embedding],
            documents=[document],
            metadatas=[clean_meta],
        )

    def query(
        self,
        embedding: list[float],
        limit: int,
        where: dict | None = None,
    ) -> list[int]:
        return [item[0] for item in self.query_detailed(embedding, limit, where=where)]

    def query_detailed(
        self,
        embedding: list[float],
        limit: int,
        where: dict | None = None,
    ) -> list[tuple[int, dict]]:
        if self._collection.count() == 0:
            return []
        kwargs: dict = {
            "query_embeddings": [embedding],
            "n_results": min(max(limit, 1), 50),
            "include": ["metadatas"],
        }
        if where:
            kwargs["where"] = where
        result = self._collection.query(**kwargs)
        ids = result.get("ids") or [[]]
        metadatas = result.get("metadatas") or [[]]
        detailed: list[tuple[int, dict]] = []
        for item_id, metadata in zip(ids[0], metadatas[0], strict=False):
            if item_id is None:
                continue
            detailed.append((int(item_id), metadata or {}))
        return detailed

    def delete(self, question_id: int) -> None:
        self._collection.delete(ids=[str(question_id)])


_store: ChromaStore | None = None


def get_chroma_store() -> ChromaStore:
    global _store
    if _store is None:
        _store = ChromaStore()
    return _store
