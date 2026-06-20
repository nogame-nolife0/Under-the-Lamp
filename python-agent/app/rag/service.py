from app.rag.chroma_store import get_chroma_store

from app.rag.compose_utils import dedupe_by_chapter, normalize_type_counts

from app.rag.embeddings import embed_texts

from app.rag.query_parser import parse_compose_query

from app.rag.text_utils import build_embed_text



MAX_PER_CHAPTER = 1





def _build_where(condition: dict, question_type: str | None = None) -> dict | None:

    clauses: list[dict] = []

    mapping = {

        "subject": "subject",

        "grade": "grade",

        "questionType": "question_type",

        "difficulty": "difficulty",

        "chapter": "chapter",

    }

    for key, meta_key in mapping.items():

        value = condition.get(key)

        if key == "questionType" and question_type:

            value = question_type

        if value:

            clauses.append({meta_key: {"$eq": str(value)}})

    if not clauses:

        return None

    if len(clauses) == 1:

        return clauses[0]

    return {"$and": clauses}





def _query_with_fallback(

    store,

    embedding: list[float],

    needed: int,

    condition: dict,

    question_type: str | None = None,

    exclude_ids: set[int] | None = None,

) -> list[int]:

    candidate_limit = min(max(needed * 4, needed), 50)

    where = _build_where(condition, question_type=question_type)

    detailed = store.query_detailed(embedding, candidate_limit, where=where)

    selected = dedupe_by_chapter(detailed, needed, MAX_PER_CHAPTER, exclude_ids=exclude_ids)

    if len(selected) >= needed:

        return selected



    if where:

        fallback_detailed = store.query_detailed(embedding, candidate_limit, where=None)

        merged = detailed + [

            item for item in fallback_detailed if item[0] not in {row[0] for row in detailed}

        ]

        selected = dedupe_by_chapter(merged, needed, MAX_PER_CHAPTER, exclude_ids=exclude_ids)

    return selected





def embed_batch(items: list[dict]) -> dict:

    if not items:

        return {"successCount": 0, "failedIds": []}



    texts = [

        build_embed_text(

            item.get("stem"),

            item.get("subject"),

            item.get("grade"),

            item.get("chapter"),

            item.get("knowledgePoints"),

        )

        for item in items

    ]

    embeddings = embed_texts(texts)

    store = get_chroma_store()



    success_count = 0

    failed_ids: list[int] = []

    for item, text, embedding in zip(items, texts, embeddings, strict=True):

        question_id = item.get("questionId")

        if question_id is None:

            continue

        try:

            metadata = {

                "question_id": int(question_id),

                "subject": item.get("subject") or "",

                "grade": item.get("grade") or "",

                "question_type": item.get("questionType") or "",

                "difficulty": item.get("difficulty") or "",

                "chapter": item.get("chapter") or "",

            }

            store.upsert(int(question_id), embedding, text, metadata)

            success_count += 1

        except Exception:

            failed_ids.append(int(question_id))

    return {"successCount": success_count, "failedIds": failed_ids}





def rag_search(

    query: str,

    subject: str | None = None,

    grade: str | None = None,

    limit: int | None = None,

) -> dict:

    condition = parse_compose_query(query)

    if subject:

        condition["subject"] = subject

    if grade:

        condition["grade"] = grade



    type_counts = normalize_type_counts(condition.get("typeCounts"))

    requested = limit or condition.get("count") or 10

    requested = max(1, min(int(requested), 30))



    search_text = condition.get("keywords") or query

    store = get_chroma_store()

    question_ids: list[int] = []

    selected_ids: set[int] = set()



    if store.count > 0:

        query_embedding = embed_texts([search_text])[0]

        if type_counts:

            for question_type, count in type_counts.items():

                type_ids = _query_with_fallback(

                    store,

                    query_embedding,

                    count,

                    condition,

                    question_type=question_type,

                    exclude_ids=selected_ids,

                )

                for question_id in type_ids:

                    if question_id not in selected_ids:

                        question_ids.append(question_id)

                        selected_ids.add(question_id)

        else:

            question_ids = _query_with_fallback(

                store,

                query_embedding,

                requested,

                condition,

                exclude_ids=selected_ids,

            )



    compose_condition = {

        "subject": condition.get("subject"),

        "grade": condition.get("grade"),

        "questionType": condition.get("questionType"),

        "typeCounts": type_counts,

        "difficulty": condition.get("difficulty"),

        "chapter": condition.get("chapter"),

        "count": requested,

        "keywords": condition.get("keywords"),

        "query": query,

        "maxPerChapter": MAX_PER_CHAPTER,

    }

    return {

        "questionIds": question_ids,

        "matchedCount": len(question_ids),

        "composeCondition": compose_condition,

    }


