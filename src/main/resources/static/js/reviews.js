// 상태 메시지 표시를 단순화하는 유틸 함수
function displayMessage(element, message, type = 'warning') {
    if (!element) return;
    element.textContent = message;
    element.classList.remove('d-none', 'alert-warning', 'alert-danger', 'alert-success');
    element.classList.add('alert', `alert-${type}`);
    element.hidden = false;
}

function hideMessage(element) {
    if (!element) return;
    element.classList.add('d-none');
}

// 공통 JSON fetch 래퍼: 적절한 헤더를 세팅하고 에러 응답을 예외로 변환
async function fetchJson(url, options = {}) {
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    const response = await fetch(url, {
        headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            ...(csrfToken && csrfHeader ? { [csrfHeader]: csrfToken } : {}),
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

    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
        return response.json();
    }

    return response.text();
}

// 리뷰 목록 페이지에서 테이블 데이터를 API로부터 채워 넣는다
async function loadReviewList() {
    const grid = document.getElementById('review-grid');
    const message = document.getElementById('list-message');

    if (!grid || !message) {
        return;
    }

    try {
        const reviews = await fetchJson('/reviews');

        if (!reviews.length) {
            grid.innerHTML = '';
            displayMessage(message, '등록된 리뷰가 없습니다.', 'warning');
            return;
        }

        hideMessage(message);
        grid.innerHTML = '';

        reviews.forEach((review) => {
            const integration = review.integrationStatus || {};
            const col = document.createElement('div');
            col.className = 'col-12 col-md-6 col-lg-4';

            const statusBadge = (label, status) => {
                const normalized = (status || 'SUCCESS').toUpperCase();
                const classMap = {
                    SUCCESS: 'badge-soft-success',
                    FAILED: 'badge-soft-danger',
                    SKIPPED: 'badge-soft-warning',
                };
                const badge = document.createElement('span');
                badge.className = `badge ${classMap[normalized] || 'badge-soft-info'}`;
                badge.textContent = `${label}: ${normalized}`;
                return badge;
            };

            col.innerHTML = `
                <div class="card border-0 shadow-sm h-100 card-hover">
                    <div class="card-body d-flex flex-column gap-3">
                        <div class="d-flex justify-content-between gap-2 align-items-start">
                            <div>
                                <h2 class="card-title h5 mb-1 text-dark">${review.title}</h2>
                                <p class="text-secondary small mb-1">${review.formattedCreatedAt || review.createdAt || '-'} • 토큰 ${review.tokenCount || 0}</p>
                                <div class="d-flex flex-wrap gap-2">
                                    <span class="badge bg-light text-success border">USD ${review.formattedUsdCost || review.usdCost || '-'}</span>
                                    <span class="badge bg-light text-success border">KRW ${review.formattedKrwCost || review.krwCost || '-'}</span>
                                </div>
                            </div>
                            <a class="btn btn-sm btn-outline-primary" href="/reviews/${review.id}">열람</a>
                        </div>
                        <div class="d-flex flex-wrap gap-2 status-badges"></div>
                        <div class="d-flex align-items-center gap-2 text-secondary small">
                            <i class="bi bi-hdd"></i>
                            <span>${review.googleFileId ? `Drive ID: ${review.googleFileId}` : 'Drive 업로드 없음'}</span>
                        </div>
                    </div>
                </div>
            `;

            const badgeContainer = col.querySelector('.status-badges');
            badgeContainer.appendChild(statusBadge('OpenAI', integration.openAiStatus));
            badgeContainer.appendChild(statusBadge('Currency', integration.currencyStatus));
            badgeContainer.appendChild(statusBadge('Drive', integration.driveStatus));

            grid.appendChild(col);
        });
    } catch (error) {
        grid.innerHTML = '';
        displayMessage(message, `목록을 불러오는 데 실패했습니다: ${error.message}`, 'danger');
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
        hideMessage(message);

        document.getElementById('detail-title').textContent = review.title;
        document.getElementById('detail-created-at').textContent = review.formattedCreatedAt || review.createdAt;
        document.getElementById('detail-token').textContent = review.tokenCount ?? '-';
        document.getElementById('detail-usd').textContent = review.formattedUsdCost || review.usdCost || '-';
        document.getElementById('detail-krw').textContent = review.formattedKrwCost || review.krwCost || '-';
        document.getElementById('detail-improved').textContent = review.improvedContent;

        const googleInfo = document.getElementById('detail-google');
        const googleId = document.getElementById('detail-google-id');
        if (review.googleFileId) {
            googleId.textContent = review.googleFileId;
            googleInfo.hidden = false;
        } else if (googleInfo) {
            googleInfo.hidden = true;
        }

        const statusWrapper = document.getElementById('detail-status');
        const integrationStatus = review.integrationStatus || {};
        const statusBadgeContainer = document.getElementById('detail-status-badges');
        const statusBadge = (elementId, status) => {
            const el = document.getElementById(elementId);
            if (el) {
                el.textContent = status || '-';
            }
        };

        if (statusWrapper) {
            statusBadge('detail-openai-status', integrationStatus.openAiStatus);
            statusBadge('detail-currency-status', integrationStatus.currencyStatus);
            statusBadge('detail-drive-status', integrationStatus.driveStatus);
            statusBadge('detail-openai-status-tile', integrationStatus.openAiStatus);
            statusBadge('detail-currency-status-tile', integrationStatus.currencyStatus);
            statusBadge('detail-drive-status-tile', integrationStatus.driveStatus);

            if (statusBadgeContainer) {
                const badgeElements = statusBadgeContainer.querySelectorAll('.badge');
                const mapClass = (status) => {
                    const normalized = (status || '').toUpperCase();
                    if (normalized === 'FAILED') return 'badge-soft-danger';
                    if (normalized === 'SKIPPED') return 'badge-soft-warning';
                    return 'badge-soft-success';
                };
                ['openAiStatus', 'currencyStatus', 'driveStatus'].forEach((key, index) => {
                    const badge = badgeElements[index];
                    if (badge) {
                        badge.className = `badge ${mapClass(integrationStatus[key])}`;
                    }
                });
            }

            const warningBlock = document.getElementById('detail-warning');
            const hasSkipped = ['openAiStatus', 'currencyStatus', 'driveStatus']
                .some((key) => (integrationStatus[key] || '').toUpperCase() === 'SKIPPED');
            if (integrationStatus.warningMessage || hasSkipped) {
                const warningMessage = integrationStatus.warningMessage
                    || '일부 외부 연동이 처리되지 않았습니다. 정보를 확인해주세요.';
                document.getElementById('detail-warning-message').textContent = warningMessage;
                warningBlock?.classList.remove('d-none');
                warningBlock.hidden = false;
            } else if (warningBlock) {
                warningBlock.classList.add('d-none');
            }
            statusWrapper.hidden = false;
        }
    } catch (error) {
        content.hidden = true;
        displayMessage(message, `리뷰를 불러오는 데 실패했습니다: ${error.message}`, 'danger');
    }
}

// 신규 리뷰 작성 폼을 AJAX 방식으로 제출한다
function handleReviewForm() {
    const form = document.getElementById('review-form');
    const message = document.getElementById('form-message');
    const submitButton = document.getElementById('submit-btn');
    const spinner = submitButton?.querySelector('.spinner-border');

    if (!form || !message) {
        return;
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        hideMessage(message);

        const title = document.getElementById('title').value.trim();
        const originalContent = document.getElementById('originalContent').value.trim();

        if (submitButton) {
            submitButton.disabled = true;
            spinner?.classList.remove('d-none');
        }

        if (!title || !originalContent) {
            displayMessage(message, '제목과 원본 독후감을 모두 입력해주세요.', 'warning');
            if (submitButton) {
                submitButton.disabled = false;
                spinner?.classList.add('d-none');
            }
            return;
        }

        try {
            const review = await fetchJson('/reviews', {
                method: 'POST',
                body: JSON.stringify({ title, originalContent }),
            });

            if (typeof review === 'object' && review?.savedReviewId) {
                if (review.message) {
                    displayMessage(message, review.message, 'success');
                }
                window.location.href = `/reviews/${review.savedReviewId}`;
                return;
            }

            // HTML 응답(로그인 페이지 등)이 돌아온 경우 사용자에게 안내하고 필요 시 리다이렉트
            if (typeof review === 'string') {
                const redirectToLogin = review.toLowerCase().includes('login');
                displayMessage(message, redirectToLogin ? '로그인이 필요합니다. 로그인 페이지로 이동합니다.' : '응답을 처리할 수 없습니다. 다시 시도해주세요.', redirectToLogin ? 'warning' : 'danger');
                if (redirectToLogin) {
                    window.location.href = '/login';
                }
                return;
            }

            displayMessage(message, '응답을 처리할 수 없습니다. 다시 시도해주세요.', 'danger');
        } catch (error) {
            displayMessage(message, `등록에 실패했습니다: ${error.message}`, 'danger');
        } finally {
            if (submitButton) {
                submitButton.disabled = false;
                spinner?.classList.add('d-none');
            }
        }
    });
}

window.addEventListener('DOMContentLoaded', () => {
    loadReviewList();
    loadReviewDetail();
    handleReviewForm();
});
