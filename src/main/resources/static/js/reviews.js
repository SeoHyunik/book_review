// 상태 메시지 표시를 단순화하는 유틸 함수
function displayMessage(element, message) {
    if (!element) return;
    element.textContent = message;
    element.hidden = false;
}

// 공통 JSON fetch 래퍼: 적절한 헤더를 세팅하고 에러 응답을 예외로 변환
async function fetchJson(url, options = {}) {
    const response = await fetch(url, {
        headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            ...(options.headers || {}),
        },
        ...options,
    });

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || `요청 실패: ${response.status}`);
    }

    if (response.status === 204) {
        return null;
    }

    return response.json();
}

// 리뷰 목록 페이지에서 테이블 데이터를 API로부터 채워 넣는다
async function loadReviewList() {
    const table = document.getElementById('review-table');
    const tableBody = document.getElementById('review-table-body');
    const message = document.getElementById('list-message');

    if (!table || !tableBody || !message) {
        return;
    }

    try {
        const reviews = await fetchJson('/reviews');

        if (!reviews.length) {
            table.hidden = true;
            displayMessage(message, '등록된 리뷰가 없습니다.');
            return;
        }

        message.hidden = true;
        table.hidden = false;
        tableBody.innerHTML = '';

        reviews.forEach((review) => {
            const row = document.createElement('tr');
            const titleCell = document.createElement('td');
            const link = document.createElement('a');
            link.href = `/reviews/${review.id}`;
            link.textContent = review.title;
            titleCell.appendChild(link);

            const createdAtCell = document.createElement('td');
            createdAtCell.textContent = review.formattedCreatedAt || review.createdAt;

            const tokenCell = document.createElement('td');
            tokenCell.textContent = review.tokenCount;

            row.appendChild(titleCell);
            row.appendChild(createdAtCell);
            row.appendChild(tokenCell);
            tableBody.appendChild(row);
        });
    } catch (error) {
        table.hidden = true;
        displayMessage(message, `목록을 불러오는 데 실패했습니다: ${error.message}`);
    }
}

// 상세 페이지에서 리뷰 내용을 API로 불러와 표시한다
async function loadReviewDetail() {
    const wrapper = document.getElementById('review-detail');
    const message = document.getElementById('detail-message');
    const content = document.getElementById('detail-content');

    if (!wrapper || !message || !content) {
        return;
    }

    const reviewId = wrapper.dataset.reviewId || window.location.pathname.split('/').pop();

    if (!reviewId) {
        displayMessage(message, '리뷰 ID를 확인할 수 없습니다.');
        return;
    }

    try {
        const review = await fetchJson(`/reviews/${reviewId}`);

        content.hidden = false;
        message.hidden = true;

        document.getElementById('detail-title').textContent = review.title;
        document.getElementById('detail-created-at').textContent = review.formattedCreatedAt || review.createdAt;
        document.getElementById('detail-token').textContent = review.tokenCount;
        document.getElementById('detail-usd').textContent = review.formattedUsdCost || review.usdCost;
        document.getElementById('detail-krw').textContent = review.formattedKrwCost || review.krwCost;
        document.getElementById('detail-improved').textContent = review.improvedContent;

        const googleInfo = document.getElementById('detail-google');
        const googleId = document.getElementById('detail-google-id');
        if (review.googleFileId) {
            googleId.textContent = review.googleFileId;
            googleInfo.hidden = false;
        } else {
            googleInfo.hidden = true;
        }
    } catch (error) {
        content.hidden = true;
        displayMessage(message, `리뷰를 불러오는 데 실패했습니다: ${error.message}`);
    }
}

// 신규 리뷰 작성 폼을 AJAX 방식으로 제출한다
function handleReviewForm() {
    const form = document.getElementById('review-form');
    const message = document.getElementById('form-message');

    if (!form || !message) {
        return;
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        message.hidden = true;

        const title = document.getElementById('title').value.trim();
        const originalContent = document.getElementById('originalContent').value.trim();

        if (!title || !originalContent) {
            displayMessage(message, '제목과 원본 독후감을 모두 입력해주세요.');
            return;
        }

        try {
            const review = await fetchJson('/reviews', {
                method: 'POST',
                body: JSON.stringify({ title, originalContent }),
            });

            window.location.href = `/reviews/${review.id}`;
        } catch (error) {
            displayMessage(message, `등록에 실패했습니다: ${error.message}`);
        }
    });
}

window.addEventListener('DOMContentLoaded', () => {
    loadReviewList();
    loadReviewDetail();
    handleReviewForm();
});
