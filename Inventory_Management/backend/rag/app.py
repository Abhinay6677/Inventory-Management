from __future__ import annotations

import streamlit as st

from rag.rag_chain import ask_question, build_rag_chain

st.set_page_config(page_title="POC-07 Inventory Manual Q&A", layout="wide")
st.title("POC-07 Inventory Manual Q&A")
st.caption("Phase 2 RAG assistant for inventory and procurement policies")

if "rag_chain" not in st.session_state:
    st.session_state["rag_chain"] = build_rag_chain()
if "messages" not in st.session_state:
    st.session_state["messages"] = []

for msg in st.session_state["messages"]:
    with st.chat_message(msg["role"]):
        st.markdown(msg["content"])

query = st.chat_input("Ask about SKU rules, reorder points, PO lifecycle, suppliers, or stock alerts")
if query:
    st.session_state["messages"].append({"role": "user", "content": query})
    with st.chat_message("user"):
        st.markdown(query)

    response = ask_question(query, st.session_state["rag_chain"])
    answer = response.get("answer", "No answer generated.")
    st.session_state["messages"].append({"role": "assistant", "content": answer})

    with st.chat_message("assistant"):
        st.markdown(answer)
