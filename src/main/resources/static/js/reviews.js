// 상태 메시지 표시를 단순화하는 유틸 함수
function displayMessage(element, message, type = 'warning') {
    if (!element) return;
    element.innerHTML = (message || '').toString().replace(/\n/g, '<br>');
    element.classList.remove('d-none', 'alert-warning', 'alert-danger', 'alert-success');
    element.classList.add('alert', `alert-${type}`);
    element.hidden = false;
    element.style.display = 'block';
}

const DELETE_CONFIRM_MESSAGE = 'Delete this review? This action will also remove the Google Drive file.';

function setupDeleteConfirmation(reviewId, messageElement) {
    const deleteButton = document.getElementById('detail-delete');
    const modalElement = document.getElementById('deleteConfirmModal');
    const confirmButton = document.getElementById('delete-confirm-btn');
    const cancelButton = document.getElementById('delete-cancel-btn');
    const deleteForm = deleteButton?.closest('form');

    if (!deleteButton) return;

    const performDeletion = async () => {
        try {
            const response = await fetchJson(`/reviews/${reviewId}`, { method: 'DELETE' });
            if (response?.warnings?.length) {
                displayMessage(messageElement, response.warnings.join('\n'), 'warning');
            }
            window.location.href = '/reviews';
        } catch (err) {
            displayMessage(messageElement, 'Failed to delete the review. Please try again later.', 'danger');
        }
    };

    const wireFallbackConfirm = () => {
        deleteButton.addEventListener('click', (event) => {
            if (!confirm(DELETE_CONFIRM_MESSAGE)) {
                event.preventDefault();
                return;
            }

            if (deleteForm) {
                deleteForm.submit();
            } else {
                performDeletion();
            }
        });
    };

    if (modalElement && confirmButton && window.bootstrap?.Modal) {
        const modalInstance = new bootstrap.Modal(modalElement, {
            backdrop: true,
            keyboard: true,
            focus: true,
        });

        modalElement.addEventListener('shown.bs.modal', () => {
            confirmButton?.focus();
        });

        deleteButton.type = 'button';
        deleteButton.addEventListener('click', (event) => {
            event.preventDefault();
            modalInstance.show();
        });

        confirmButton.addEventListener('click', async () => {
            confirmButton.disabled = true;
            const originalText = confirmButton.textContent;
            confirmButton.textContent = 'Deleting...';
            await performDeletion();
            confirmButton.disabled = false;
            confirmButton.textContent = originalText;
            modalInstance.hide();
        });

        cancelButton?.addEventListener('click', () => {
            modalInstance.hide();
        });
    } else {
        wireFallbackConfirm();
    }
}

function hideMessage(element) {
    if (!element) return;
    element.classList.add('d-none');
    element.style.display = 'none';
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
    const deleteButton = document.getElementById('detail-delete');

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

        setupDeleteConfirmation(reviewId, message);

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
                document.getElementById('detail-warning-message').innerHTML = warningMessage.replace(/\n/g, '<br>');
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

window.addEventListener('DOMContentLoaded', () => {
    loadReviewList();
    loadReviewDetail();
    // 리뷰 작성 폼 제출 시 로딩 오버레이 표시를 초기화합니다.
    setupReviewFormLoading();
});

// 폼 제출 시 로딩 오버레이를 표시
function setupReviewFormLoading() {
    const form = document.getElementById('review-form');
    const overlay = document.getElementById('loading-overlay');
    if (form && overlay) {
        form.addEventListener('submit', () => {
            // 배경은 overlay의 backdrop-filter로만 블러 처리하고,
            // 아이콘은 선명하게 보이도록 body에는 filter를 적용하지 않는다.
            overlay.classList.remove('d-none', 'fade-out');
        });
    }
}
