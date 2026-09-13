import axios from "@/axios";

// 接口前缀
const API_PREFIX = '/relation'

// 关注用户
export function followUser(followUserId) {
    return axios.post(`${API_PREFIX}/follow`, {followUserId})
}

// 取消关注用户
export function unfollowUser(unfollowUserId) {
    return axios.post(`${API_PREFIX}/unfollow`, {unfollowUserId})
}

// 获取关注列表
export function getFollowingList(userId, pageNo) {
    return axios.post(`${API_PREFIX}/following/list`, {userId, pageNo})
}

// 获取粉丝列表
export function getFansList(userId, pageNo) {
    return axios.post(`${API_PREFIX}/fans/list`, {userId, pageNo})
}

// 查询当前登录用户是否已关注目标用户
export function isFollowedUser(userId) {
    return axios.post(`${API_PREFIX}/isFollowed`, {followUserId: userId})
}


